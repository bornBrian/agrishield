import { useState } from "react";
import { anomalyApi } from "../agrishield";

export default function AnomalyTable({ anomalies, onResolved }) {
  const [resolvingId, setResolvingId] = useState(null);

  const handleResolve = async (anomalyId) => {
    const notes = prompt("Enter resolution notes (what was found, what action taken):");
    if (!notes || notes.trim().length < 10) {
      alert("Notes must be at least 10 characters.");
      return;
    }

    setResolvingId(anomalyId);
    try {
      await anomalyApi.resolve(anomalyId, notes);
      onResolved();
    } catch (err) {
      alert("Failed to resolve anomaly: " + (err.response?.data?.message || "Unknown error"));
    } finally {
      setResolvingId(null);
    }
  };

  const severityColor = {
    critical: "red",
    high: "orange",
    medium: "amber",
    low: "grey",
  };

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th>Serial</th>
          <th>Type</th>
          <th>Severity</th>
          <th>Description</th>
          <th>Detected</th>
          <th>Action</th>
        </tr>
      </thead>
      <tbody>
        {anomalies.map((anomaly) => (
          <tr key={anomaly.anomalyId}>
            <td><code>{anomaly.serialCode}</code></td>
            <td><span className="badge">{anomaly.anomalyType}</span></td>
            <td>
              <span className={`badge badge--${severityColor[anomaly.severity]}`}>
                {anomaly.severity.toUpperCase()}
              </span>
            </td>
            <td>{anomaly.description}</td>
            <td>{new Date(anomaly.createdAt).toLocaleString()}</td>
            <td>
              <button
                className="btn-small btn-green"
                onClick={() => handleResolve(anomaly.anomalyId)}
                disabled={resolvingId === anomaly.anomalyId}
              >
                {resolvingId === anomaly.anomalyId ? "Resolving..." : "Resolve"}
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
