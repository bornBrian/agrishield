  // src/pages/BatchCreatePage.jsx
  
  import { useState } from "react";
  import { useNavigate } from "react-router-dom";
  import { batchApi } from "../agrishield";
  
  export default function BatchCreatePage() {
  
    const navigate = useNavigate();
  
    // All form fields in ONE state object.
    // This pattern (one object for all fields) is cleaner than
    // a separate useState for every field.
    const [form, setForm] = useState({
      productCode:     "",
      batchCode:       "",
      quantity:        "",
      manufactureDate: "",
      expiryDate:      "",
      description:     "",
    });
  
    const [labCertFile, setLabCertFile] = useState(null);
    const [errors,  setErrors]  = useState({});  // per-field errors
    const [loading, setLoading] = useState(false);
    const [serverError, setServerError] = useState(null);
  
    // ── GENERIC CHANGE HANDLER ────────────────────────────────────────
    // Works for any field — reads the field name from e.target.name
    // and updates only that key in the form state.
    const handleChange = (e) => {
      const { name, value } = e.target;
      setForm(prev => ({ ...prev, [name]: value }));
      // Clear the error for this field as the user types
      if (errors[name]) setErrors(prev => ({ ...prev, [name]: null }));
    };
  
    // ── CLIENT-SIDE VALIDATION ────────────────────────────────────────
    // Always validate on the client AND on the server.
    // Client validation = fast feedback. Server validation = real security.
    const validate = () => {
      const newErrors = {};
      if (!form.productCode)  newErrors.productCode  = "Product is required.";
      if (!form.batchCode)    newErrors.batchCode    = "Batch number is required.";
      if (!form.quantity || form.quantity < 1)
        newErrors.quantity = "Quantity must be at least 1.";
      if (!form.manufactureDate)
        newErrors.manufactureDate = "Manufacture date is required.";
      if (!form.expiryDate)
        newErrors.expiryDate = "Expiry date is required.";
      if (form.expiryDate && form.manufactureDate &&
          new Date(form.expiryDate) <= new Date(form.manufactureDate))
        newErrors.expiryDate = "Expiry must be after manufacture date.";
      if (!labCertFile)
        newErrors.labCert = "Lab certificate PDF is required.";
      return newErrors;
    };
  
    // ── SUBMIT ────────────────────────────────────────────────────────
    const handleSubmit = async (e) => {
      e.preventDefault();
  
      const validationErrors = validate();
      if (Object.keys(validationErrors).length > 0) {
        setErrors(validationErrors);
        return;  // stop here — show errors to user
      }
  
      setLoading(true);
      setServerError(null);
  
      try {
        // Step 1: Create the batch (status = DRAFT)
        const createRes = await batchApi.create({
          ...form,
          quantity: parseInt(form.quantity),
        });
        const newBatchId = createRes.data.batchId;
  
        // Step 2: Upload the lab certificate to the draft batch
        await batchApi.uploadLabCert(newBatchId, labCertFile);
  
        // Step 3: Submit for TFDA review (status → PENDING)
        await batchApi.submit(newBatchId);
  
        // Success! Go to dashboard
        navigate("/dashboard", {
          state: { successMessage: "Batch submitted for TFDA review." }
        });
  
      } catch (err) {
        setServerError(err.response?.data?.message || "Failed to create batch.");
      } finally {
        setLoading(false);
      }
    };
  
    // ── RENDER ────────────────────────────────────────────────────────
    return (
      <div className="page">
        <div className="page-header">
          <h2>Submit New Batch for Approval</h2>
          <p>All fields are required. Your batch will be reviewed by two TFDA officers.</p>
        </div>
  
        {serverError && <div className="alert alert-error">{serverError}</div>}
  
        <form onSubmit={handleSubmit} className="form-card">
  
          {/* Product selection */}
          <div className={`form-group ${errors.productCode ? "has-error" : ""}`}>
            <label>Product Code *</label>
            <input name="productCode" value={form.productCode}
              onChange={handleChange} placeholder="e.g. FERT-50" />
            {errors.productCode && <span className="error">{errors.productCode}</span>}
          </div>
  
          {/* Two-column layout for batch code + quantity */}
          <div className="form-row">
            <div className={`form-group ${errors.batchCode ? "has-error" : ""}`}>
              <label>Batch Number *</label>
              <input name="batchCode" value={form.batchCode}
                onChange={handleChange} placeholder="e.g. FERT-2025-001" />
              {errors.batchCode && <span className="error">{errors.batchCode}</span>}
            </div>
            <div className={`form-group ${errors.quantity ? "has-error" : ""}`}>
              <label>Quantity (units) *</label>
              <input name="quantity" type="number" min="1"
                value={form.quantity} onChange={handleChange} />
              {errors.quantity && <span className="error">{errors.quantity}</span>}
            </div>
          </div>
  
          <div className="form-row">
            <div className={`form-group ${errors.manufactureDate ? "has-error" : ""}`}>
              <label>Manufacture Date *</label>
              <input name="manufactureDate" type="date"
                value={form.manufactureDate} onChange={handleChange} />
              {errors.manufactureDate && <span className="error">{errors.manufactureDate}</span>}
            </div>
            <div className={`form-group ${errors.expiryDate ? "has-error" : ""}`}>
              <label>Expiry Date *</label>
              <input name="expiryDate" type="date"
                value={form.expiryDate} onChange={handleChange} />
              {errors.expiryDate && <span className="error">{errors.expiryDate}</span>}
            </div>
          </div>
  
          {/* File upload for lab certificate */}
          <div className={`form-group ${errors.labCert ? "has-error" : ""}`}>
            <label>Lab Test Certificate (PDF) *</label>
            <input type="file" accept=".pdf"
              onChange={(e) => setLabCertFile(e.target.files[0])} />
            {labCertFile && <small>Selected: {labCertFile.name}</small>}
            {errors.labCert && <span className="error">{errors.labCert}</span>}
          </div>
  
          <div className="form-actions">
            <button type="button" className="btn-secondary"
              onClick={() => navigate("/dashboard")}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? "Submitting..." : "Submit for TFDA Review →"}
            </button>
          </div>
  
        </form>
      </div>
    );
  }
