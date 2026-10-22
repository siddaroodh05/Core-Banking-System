import { useEffect, useState } from 'react';
import { verifyReceiver } from '../api/accountApi';
import { beginTransferAttempt, clearTransferAttempt, getTransferAttempt, retryTransferAttempt } from '../api/transactionApi';
import { useBank } from '../context/BankContext';
import { Alert, Button, Field, PageHead, SelectField, Skeleton, TextField } from '../components/ui';
import { Icon } from '../components/Icon';
import { Link, navigate } from '../router';
import { maskAccount, money, newIdempotencyKey, titleCase } from '../utils/format';
import { TransactionReceipt } from '../components/TransactionReceipt';

const DRAFT_KEY = 'banking-ui-transfer-draft';
const resultKey = (key) => `banking-ui-payment-result:${key}`;

function readDraft() {
  try {
    const draft = JSON.parse(sessionStorage.getItem(DRAFT_KEY));
    return draft && typeof draft === 'object' ? draft : null;
  } catch { return null; }
}

function readResult(key) {
  try {
    const result = JSON.parse(sessionStorage.getItem(resultKey(key)));
    return result && typeof result === 'object' ? result : null;
  } catch { return null; }
}

function failedTransferResponse(error) {
  const body = error?.responseBody;
  if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
  const candidate = [body.data, body.transaction, body.result, body]
    .find((value) => value && typeof value === 'object' && !Array.isArray(value)
      && (value.transactionId || value.amount !== undefined || value.debitedFrom || value.creditedTo
        || value.sender || value.receiver
        || ['FAILED', 'FAILURE', 'DECLINED', 'REJECTED'].includes(String(value.status || value.transactionStatus || '').toUpperCase())));
  if (!candidate) return null;
  return {
    ...candidate,
    status: candidate.status || candidate.transactionStatus || 'FAILED',
    failureReason: candidate.failureReason || candidate.reason || candidate.errorMessage || error.message,
  };
}

export function TransferDetailsPage() {
  const { accounts, active } = useBank();
  const savedDraft = readDraft();
  const [from, setFrom] = useState(savedDraft?.senderAccountNumber || active?.accountNumber || accounts[0]?.accountNumber || '');
  const [to, setTo] = useState(savedDraft?.receiverAccountNumber || '');
  const [amount, setAmount] = useState(savedDraft?.amount ? String(savedDraft.amount) : '');
  const [note, setNote] = useState(savedDraft?.description || '');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [verify, setVerify] = useState({ state: 'idle' });

  useEffect(() => {
    if (!/^\d{12}$/.test(to)) {
      setVerify({ state: 'idle' });
      return undefined;
    }
    let live = true;
    setVerify({ state: 'checking' });
    verifyReceiver(to)
      .then((result) => live && setVerify({ state: result.status }))
      .catch((err) => live && setVerify({ state: 'ERROR', message: err.message }));
    return () => { live = false; };
  }, [to]);

  const submit = (event) => {
    event.preventDefault();
    const nextErrors = {};
    if (!/^\d{12}$/.test(to)) nextErrors.to = 'Account number has 12 digits.';
    else if (to === from) nextErrors.to = 'Choose a different account to send to.';
    else if (verify.state === 'ERROR') nextErrors.to = verify.message;
    else if (verify.state === 'SUSPENDED' || verify.state === 'CLOSED') nextErrors.to = 'This account cannot receive money right now.';
    else if (verify.state !== 'ACTIVE') nextErrors.to = 'Wait until the receiver account check is complete.';
    if (!(Number(amount) >= 1)) nextErrors.amount = 'Enter at least ₹1.';
    if (!accounts.some((account) => account.accountNumber === from)) nextErrors.from = 'Choose one of your linked accounts.';
    setErrors(nextErrors);
    setError('');
    if (Object.keys(nextErrors).length) return;

    try {
      sessionStorage.setItem(DRAFT_KEY, JSON.stringify({
        senderAccountNumber: from,
        receiverAccountNumber: to,
        amount: Number(amount),
        description: note.trim(),
      }));
      navigate('/transfer-pin');
    } catch {
      setError('Transfer details could not be saved in this browser. Check storage settings and try again.');
    }
  };

  return (
    <>
      <PageHead title="Send money" sub="Enter the recipient and transfer details. You will confirm the transfer with your PIN on the next step." />
      <div className="panel transfer transfer-flow">
        <div className="panel__body">
          <form className="form" onSubmit={submit} noValidate>
            {error && <Alert tone="bad">{error}</Alert>}
            <SelectField label="From account" value={from} error={errors.from} onChange={(event) => setFrom(event.target.value)}>
              {accounts.map((account) => (
                <option key={account.accountNumber} value={account.accountNumber}>
                  {titleCase(account.type)} {maskAccount(account.accountNumber)}
                </option>
              ))}
            </SelectField>
            <div className="field">
              <TextField
                label="To account number"
                inputMode="numeric"
                value={to}
                className="acct"
                placeholder="000000000000"
                onChange={(event) => setTo(event.target.value.replace(/\D/g, '').slice(0, 12))}
                error={errors.to}
              />
              <div className="verify" aria-live="polite">
                {verify.state === 'checking' && <span className="muted">Checking account…</span>}
                {verify.state === 'ACTIVE' && <span style={{ color: 'var(--credit)', display: 'inline-flex', gap: 6, alignItems: 'center' }}><Icon name="check" size={16} /> Account found and active</span>}
                {verify.state === 'SUSPENDED' && <span style={{ color: 'var(--debit)' }}>This account is suspended</span>}
                {verify.state === 'ERROR' && !errors.to && <span style={{ color: 'var(--debit)' }}>{verify.message}</span>}
              </div>
            </div>
            <Field label="Amount" error={errors.amount}>
              <div className="adorn">
                <span className="adorn__pre">₹</span>
                <input
                  className="input input--amount"
                  inputMode="decimal"
                  value={amount}
                  placeholder="0.00"
                  aria-label="Amount in rupees"
                  aria-invalid={!!errors.amount}
                  onChange={(event) => setAmount(event.target.value.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1'))}
                />
              </div>
            </Field>
            <TextField label="Note (optional)" value={note} maxLength={255} onChange={(event) => setNote(event.target.value)} hint="Shown on both passbooks." />
            <Button type="submit">Continue to PIN</Button>
          </form>
        </div>
      </div>
    </>
  );
}

export function TransferPinPage() {
  const { accounts } = useBank();
  const draft = readDraft();
  const sender = accounts.find((account) => account.accountNumber === draft?.senderAccountNumber);
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = (event) => {
    event.preventDefault();
    if (!draft || !sender) {
      setError('Transfer details are unavailable. Return to Send money and enter them again.');
      return;
    }
    if (!/^\d{6}$/.test(pin)) {
      setError('Enter your 6-digit PIN.');
      return;
    }
    if (busy) return;

    setBusy(true);
    const idempotencyKey = newIdempotencyKey();
    try {
      beginTransferAttempt({
        senderAccountNumber: draft.senderAccountNumber,
        receiverAccountNumber: draft.receiverAccountNumber,
        amount: draft.amount,
        transactionPin: pin,
        description: draft.description || undefined,
        idempotencyKey,
      });
      navigate(`/transfer-status/${encodeURIComponent(idempotencyKey)}`);
    } catch (err) {
      setBusy(false);
      setError(err.message || 'Could not start this transfer. Please try again.');
    }
  };

  return (
    <>
      <PageHead title="Confirm transfer" sub="Enter your PIN to authorize this payment." />
      <div className="panel balance-card">
        <div className="panel__body">
          <form className="form" onSubmit={submit} noValidate>
            <p className="muted">{draft && sender ? `Send ${money(draft.amount)} from ${titleCase(sender.type)} ${maskAccount(sender.accountNumber)} to account ending in ${String(draft.receiverAccountNumber).slice(-4)}.` : 'Your transfer details are unavailable.'}</p>
            {error && <Alert tone="bad">{error}</Alert>}
            <TextField
              label="Account PIN"
              type="password"
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              className="input--pin"
              placeholder="••••••"
              value={pin}
              maxLength={6}
              onChange={(event) => setPin(event.target.value.replace(/\D/g, '').slice(0, 6))}
              hint="Your 6-digit transaction PIN"
            />
            <div className="row">
              <Button type="submit" busy={busy} disabled={!draft || !sender}>{busy ? 'Processing…' : 'Confirm and send'}</Button>
              <Link to="/transfer" className="btn btn--ghost">Back</Link>
            </div>
          </form>
        </div>
      </div>
    </>
  );
}

export function TransferStatusPage({ idempotencyKey }) {
  const [attempt] = useState(() => getTransferAttempt(idempotencyKey));
  const [phase, setPhase] = useState(() => readResult(idempotencyKey) ? 'complete' : attempt?.phase || 'missing');
  const [transaction, setTransaction] = useState(() => readResult(idempotencyKey) || attempt?.result || null);
  const [error, setError] = useState(() => attempt?.error?.message || '');

  useEffect(() => {
    if (!attempt || phase === 'complete' || phase === 'missing') return undefined;
    let live = true;
    const waitForAttempt = async () => {
      const completedAttempt = await attempt.promise;
      if (!live) return;
      if (completedAttempt.phase === 'complete') {
        try {
          sessionStorage.setItem(resultKey(idempotencyKey), JSON.stringify(completedAttempt.result));
          sessionStorage.removeItem(DRAFT_KEY);
        } catch {
          setError('Payment completed, but the result could not be saved for refresh.');
        }
        setTransaction(completedAttempt.result);
        setPhase('complete');
      } else {
        const failedResponse = failedTransferResponse(completedAttempt.error);
        if (failedResponse) {
          try {
            sessionStorage.setItem(resultKey(idempotencyKey), JSON.stringify(failedResponse));
            sessionStorage.removeItem(DRAFT_KEY);
          } catch {
            setError('Payment failed, but the result could not be saved for refresh.');
          }
          setTransaction(failedResponse);
          setPhase('complete');
        } else {
          setError(completedAttempt.error?.message || 'We could not reach the bank. Retry safely using the same payment reference.');
          setPhase('error');
        }
      }
    };
    waitForAttempt();
    return () => { live = false; };
  }, [attempt, idempotencyKey, phase]);

  const retry = () => {
    const nextAttempt = retryTransferAttempt(idempotencyKey);
    if (!nextAttempt) {
      setError('This payment cannot be retried in the current browser session. Return to Send money to start again.');
      return;
    }
    setError('');
    setPhase('loading');
  };

  const startAnother = () => {
    clearTransferAttempt(idempotencyKey);
    sessionStorage.removeItem(DRAFT_KEY);
    navigate('/transfer');
  };

  return (
    <>
      <PageHead title="Payment status" sub="Your transfer request and its latest status." />
      {phase === 'loading' ? (
        <section className="panel transaction-card transaction-status-loading" aria-live="polite">
          <div className="panel__body">
            <div className="transaction-status-loading__spinner" aria-hidden="true" />
            <h2>Processing your payment</h2>
            <p className="muted">Please wait while we get confirmation from the bank. Do not submit this payment again.</p>
          </div>
        </section>
      ) : transaction ? (
        <>
          <TransactionReceipt transaction={transaction} />
          <div className="transaction-status-actions">
            <Link to="/passbook" className="btn">View passbook</Link>
            <button type="button" className="btn btn--ghost" onClick={startAnother}>Send another</button>
          </div>
        </>
      ) : phase === 'error' ? (
        <section className="panel transaction-card">
          <div className="panel__body">
            <Alert tone="bad">{error}</Alert>
            <p className="muted transaction-status__reference">Payment reference: <span className="acct">{idempotencyKey}</span></p>
            <div className="row" style={{ marginTop: 18 }}>
              <Button type="button" onClick={retry}>Retry same payment</Button>
              <button type="button" className="btn btn--ghost" onClick={startAnother}>Cancel</button>
            </div>
          </div>
        </section>
      ) : (
        <section className="panel transaction-card">
          <div className="panel__body">
            <Alert tone="info">This payment status is no longer available in this browser session. Check your passbook before attempting the payment again.</Alert>
            <div className="row" style={{ marginTop: 18 }}>
              <Link to="/passbook" className="btn">View passbook</Link>
              <Link to="/transfer" className="btn btn--ghost">Send money</Link>
            </div>
          </div>
        </section>
      )}
    </>
  );
}
