import { useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { BankProvider, useBank } from './context/BankContext';
import { ToastProvider } from './context/ToastContext';
import { navigate, useRoute } from './router';
import { AppLayout } from './components/Layout';
import { Alert, Skeleton } from './components/ui';
import { LoginPage, RegisterPage, SignupOtpPage } from './pages/AuthPages';
import { OnboardingPage } from './pages/OnboardingPage';
import { DashboardPage } from './pages/DashboardPage';
import { TransferDetailsPage, TransferPinPage, TransferStatusPage } from './pages/TransferFlowPages';
import { PassbookPage } from './pages/PassbookPage';
import { ProfilePage } from './pages/ProfilePage';
import { AdminPage } from './pages/AdminPage';
import { TransactionDetailsPage } from './pages/TransactionDetailsPage';
import { BalancePinPage, BalanceResultPage } from './pages/BalancePages';
import { AccountsPage } from './pages/AccountsPage';
import { CreateAccountPage } from './pages/CreateAccountPage';

const CUSTOMER_ROUTES = { '/': DashboardPage, '/accounts': AccountsPage, '/accounts/create': CreateAccountPage, '/transfer': TransferDetailsPage, '/transfer-pin': TransferPinPage, '/passbook': PassbookPage, '/profile': ProfilePage };

function Authed() {
  const path = useRoute();
  const { isStaff } = useAuth();
  const { loading, profile, accounts, error, reload } = useBank();
  const staffOnly = isStaff && !profile; // bank staff without a customer profile go straight to the desk
  const needsSetup = !loading && !error && !staffOnly && (!profile || accounts.length === 0);

  useEffect(() => {
    if (staffOnly && path !== '/admin') navigate('/admin');
    if (needsSetup && path !== '/onboarding') navigate('/onboarding');
    if (!needsSetup && path === '/onboarding') navigate('/');
    if (['/login', '/register'].includes(path)) navigate('/');
  }, [needsSetup, staffOnly, path]);

  if (loading) return <AppLayout><div className="stack" style={{ maxWidth: 560 }}><Skeleton h={34} w="60%" /><Skeleton h={190} /><Skeleton h={90} /></div></AppLayout>;
  if (error) return <AppLayout><div className="app-error-state"><Alert tone="bad">{error} <button className="link-btn" onClick={reload}>Try again</button></Alert></div></AppLayout>;

  let Page = CUSTOMER_ROUTES[path];
  const transactionMatch = path.match(/^\/transaction\/([^/]+)$/);
  if (transactionMatch) {
    const transactionId = decodeURIComponent(transactionMatch[1]);
    Page = () => <TransactionDetailsPage transactionId={transactionId} />;
  }
  const balanceMatch = path.match(/^\/balance\/([^/]+)$/);
  if (balanceMatch) {
    const accountNumber = decodeURIComponent(balanceMatch[1]);
    Page = () => <BalancePinPage accountNumber={accountNumber} />;
  }
  const balanceResultMatch = path.match(/^\/balance-result\/([^/]+)$/);
  if (balanceResultMatch) {
    const accountNumber = decodeURIComponent(balanceResultMatch[1]);
    Page = () => <BalanceResultPage accountNumber={accountNumber} />;
  }
  const transferStatusMatch = path.match(/^\/transfer-status\/([^/]+)$/);
  if (transferStatusMatch) {
    const idempotencyKey = decodeURIComponent(transferStatusMatch[1]);
    Page = () => <TransferStatusPage idempotencyKey={idempotencyKey} />;
  }
  if (path === '/onboarding') Page = OnboardingPage;
  if (path === '/admin') Page = isStaff ? AdminPage : DashboardPage;
  if (!Page) Page = DashboardPage;
  if (staffOnly) Page = AdminPage;
  if (needsSetup) Page = OnboardingPage;
  return <AppLayout><Page /></AppLayout>;
}

function Gate() {
  const { user } = useAuth();
  const path = useRoute();
  const email = new URLSearchParams(window.location.hash.split('?')[1] || '').get('email') || '';
  useEffect(() => { if (!user && path !== '/login' && path !== '/register' && path !== '/verify-otp') navigate('/login'); }, [user, path]);
  if (!user) {
    if (path === '/register') return <RegisterPage />;
    if (path === '/verify-otp') return <SignupOtpPage email={email} />;
    return <LoginPage />;
  }
  return <BankProvider><Authed /></BankProvider>;
}

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Gate />
      </AuthProvider>
    </ToastProvider>
  );
}
