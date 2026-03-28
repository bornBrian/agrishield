export default function StatCard({ value, label, color = "green", icon }) {
  return (
    <div className={`stat-card stat-card--${color}`}>
      {icon && <span className="stat-icon">{icon}</span>}
      <div className="stat-value">{value ?? "—"}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}
