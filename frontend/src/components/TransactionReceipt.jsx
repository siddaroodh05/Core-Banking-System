import { groupAccount, money, shortDate, shortTime } from '../utils/format';

export function TransactionReceipt({ transaction }) {
  const rawStatus = String(
    transaction?.status || transaction?.transactionStatus || transaction?.paymentStatus
    || (transaction?.success === false ? 'FAILED' : 'UNKNOWN'),
  ).toUpperCase();
  const isFailed = ['FAILED', 'FAILURE', 'FAIL', 'DECLINED', 'REJECTED'].includes(rawStatus);
  const normalizedStatus = isFailed ? 'FAILED' : rawStatus;
  const status = {
    SUCCESS: { title: 'Transaction successful', icon: 'success' },
    FAILED: { title: 'Transaction failed', icon: 'failed' },
    PENDING: { title: 'Transaction pending', icon: 'pending' },
  }[normalizedStatus];
  const failureReason = transaction?.failureReason || transaction?.reason
    || transaction?.errorMessage || transaction?.message;
  const sender = transaction?.debitedFrom || transaction?.sender;
  const receiver = transaction?.creditedTo || transaction?.receiver;
  const timestamp = transaction?.completedAt || transaction?.createdAt || transaction?.timestamp;
  const transactionId = transaction?.transactionId || transaction?.referenceNumber || transaction?.id;

  return (
    <section className="panel transaction-card">
      <div className={`transaction-card__receipt transaction-card__receipt--${status?.icon || 'unknown'}`}>
        <div className="transaction-card__status">
          <span className="transaction-card__status-icon" aria-hidden="true">
            {status?.icon === 'success' ? (
              <svg viewBox="0 0 24 24" fill="none"><path d="m5 12 4 4L19 6" /></svg>
            ) : status?.icon === 'failed' ? (
              <svg viewBox="0 0 24 24" fill="none"><path d="m7 7 10 10M17 7 7 17" /></svg>
            ) : <span>!</span>}
          </span>
          <h2>{status?.title || `Transaction ${rawStatus.toLowerCase()}`}</h2>
        </div>
        {isFailed && (
          <p className="transaction-card__reason">
            {failureReason || 'The transfer did not go through. No money left your account.'}
          </p>
        )}
        <strong className="transaction-card__amount">{money(transaction?.amount)}</strong>
        <span className="transaction-card__time">{shortDate(timestamp)} · {shortTime(timestamp)}</span>
      </div>
      <dl className="transaction-card__facts">
        {transactionId && (
          <div className="transaction-card__fact transaction-card__fact--reference">
            <dt>Reference number</dt>
            <dd className="acct">{transactionId}</dd>
          </div>
        )}
        {sender && (
          <div className="transaction-card__fact">
            <dt>Sender</dt>
            <dd>{sender.accountHolderName || sender.name || '—'}{(sender.accountNumber || sender.accountNo) && <span className="acct">{groupAccount(sender.accountNumber || sender.accountNo)}</span>}</dd>
          </div>
        )}
        {receiver && (
          <div className="transaction-card__fact">
            <dt>Receiver</dt>
            <dd>{receiver.accountHolderName || receiver.name || '—'}{(receiver.accountNumber || receiver.accountNo) && <span className="acct">{groupAccount(receiver.accountNumber || receiver.accountNo)}</span>}</dd>
          </div>
        )}
        {transaction?.description && (
          <div className="transaction-card__fact transaction-card__fact--reference">
            <dt>Description</dt>
            <dd>{transaction.description}</dd>
          </div>
        )}
      </dl>
    </section>
  );
}
