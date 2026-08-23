import SystemInfoPage from './pages/SystemInfoPage';

export default function App() {
  return (
    <main className="mc-shell">
      <h1 className="mc-title">Mission Control</h1>
      <p className="mc-subtitle">Space mission planning and crew assignment.</p>
      <SystemInfoPage />
    </main>
  );
}
