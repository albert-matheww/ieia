import { Link } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <div className="grid min-h-screen place-items-center bg-surface px-6 text-center">
      <div>
        <p className="font-mono text-[52px] font-semibold text-content">404</p>
        <p className="mt-1 text-[14px] text-content-muted">That page isn't on the console.</p>
        <Link to="/" className="btn btn-md btn-primary mt-5">Back to dashboard</Link>
      </div>
    </div>
  );
}
