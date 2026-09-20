import React, { useState } from 'react';
import {
  KeyRound,
  Plus,
  Trash2,
  Check,
  Copy,
  Eye,
  EyeOff,
  ExternalLink,
  ShieldAlert,
  Sparkles,
  ArrowLeft,
  CheckCircle2,
  Loader2,
  User,
} from 'lucide-react';
import { UserApiKey } from '../types';
import { validateHfToken } from '../services/api';

interface ApiKeysManagerProps {
  apiKeys: UserApiKey[];
  onAddKey: (name: string, token: string) => void;
  onDeleteKey: (id: string) => void;
  onSetDefaultKey: (id: string) => void;
  onBack: () => void;
}

export const ApiKeysManager: React.FC<ApiKeysManagerProps> = ({
  apiKeys,
  onAddKey,
  onDeleteKey,
  onSetDefaultKey,
  onBack,
}) => {
  const [isAdding, setIsAdding] = useState(false);
  const [name, setName] = useState('');
  const [token, setToken] = useState('');
  const [visibleKeys, setVisibleKeys] = useState<Record<string, boolean>>({});
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [validatingId, setValidatingId] = useState<string | null>(null);
  const [validationResults, setValidationResults] = useState<Record<string, any>>({});
  const [error, setError] = useState<string | null>(null);

  const toggleVisibility = (id: string) => {
    setVisibleKeys((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const handleCopy = (id: string, tokenVal: string) => {
    navigator.clipboard.writeText(tokenVal);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleValidate = async (id: string, tokenVal: string) => {
    setValidatingId(id);
    try {
      const res = await validateHfToken(tokenVal);
      setValidationResults((prev) => ({
        ...prev,
        [id]: { valid: true, user: res.user },
      }));
    } catch (err: any) {
      setValidationResults((prev) => ({
        ...prev,
        [id]: { valid: false, error: err.message || 'Invalid token' },
      }));
    } finally {
      setValidatingId(null);
    }
  };

  const handleSave = () => {
    const trimmedToken = token.trim();
    if (!trimmedToken) {
      setError('Please enter a Hugging Face token');
      return;
    }
    if (!trimmedToken.startsWith('hf_')) {
      setError("Hugging Face tokens usually start with 'hf_'");
    }

    const keyName = name.trim() || `Key ${apiKeys.length + 1}`;
    onAddKey(keyName, trimmedToken);
    setName('');
    setToken('');
    setIsAdding(false);
    setError(null);
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-50 dark:bg-slate-950 overflow-hidden">
      {/* Header */}
      <header className="px-4 py-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between shrink-0 shadow-xs">
        <div className="flex items-center gap-2.5">
          <button
            onClick={onBack}
            className="p-1.5 rounded-lg text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            aria-label="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
              <KeyRound className="w-4 h-4 text-amber-500" />
              API Keys
            </h2>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Manage user access tokens for Hugging Face Spaces
            </p>
          </div>
        </div>

        {!isAdding && (
          <button
            id="btn-add-api-key"
            onClick={() => setIsAdding(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 text-xs font-semibold hover:opacity-90 transition-opacity cursor-pointer shadow-xs"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Key</span>
          </button>
        )}
      </header>

      {/* Main Content Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 max-w-2xl mx-auto w-full">
        {/* Add New Key Form */}
        {isAdding && (
          <div className="p-4 rounded-2xl bg-white dark:bg-slate-900 border border-amber-500/30 dark:border-amber-500/20 shadow-sm space-y-3.5 animate-in fade-in slide-in-from-top-2 duration-150">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-semibold text-slate-900 dark:text-white flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                Add New Hugging Face Token
              </h3>
              <button
                onClick={() => {
                  setIsAdding(false);
                  setError(null);
                }}
                className="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 cursor-pointer"
              >
                Cancel
              </button>
            </div>

            <div className="space-y-2.5">
              <div>
                <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-400 mb-1">
                  Key Label (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. Personal Pro Token, ZeroGPU Quota"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/80 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-[11px] font-medium text-slate-600 dark:text-slate-400 mb-1">
                  Access Token <span className="text-amber-600 dark:text-amber-400">*</span>
                </label>
                <input
                  type="password"
                  placeholder="hf_xxxxxxxxxxxxxxxxxxxxxxxxx"
                  value={token}
                  onChange={(e) => {
                    setToken(e.target.value);
                    if (error) setError(null);
                  }}
                  className="w-full px-3 py-2 text-xs font-mono rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/80 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden focus:border-amber-500"
                />
              </div>

              {error && (
                <p className="text-[11px] text-rose-500 font-medium">{error}</p>
              )}
            </div>

            <div className="pt-2 flex items-center justify-between gap-3">
              <a
                href="https://huggingface.co/settings/tokens"
                target="_blank"
                rel="noreferrer"
                className="text-[11px] text-amber-600 dark:text-amber-400 hover:underline flex items-center gap-1"
              >
                <span>Get token from Hugging Face</span>
                <ExternalLink className="w-3 h-3" />
              </a>

              <button
                onClick={handleSave}
                className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-600 text-slate-950 font-semibold text-xs transition-colors cursor-pointer shadow-xs"
              >
                Save API Key
              </button>
            </div>
          </div>
        )}

        {/* Keys List */}
        {apiKeys.length === 0 && !isAdding ? (
          <div className="py-16 text-center space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/10 text-amber-500 flex items-center justify-center mx-auto">
              <KeyRound className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
              No API Keys Saved
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm mx-auto leading-relaxed">
              Add your Hugging Face user access token to bypass anonymous rate limits, access private spaces, and use your personal ZeroGPU quota.
            </p>
            <div className="pt-2">
              <button
                onClick={() => setIsAdding(true)}
                className="px-4 py-2 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 text-xs font-semibold hover:opacity-90 transition-opacity cursor-pointer"
              >
                Add Your First Key
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-2.5">
            {apiKeys.map((k) => {
              const isVisible = !!visibleKeys[k.id];
              const masked = k.token.length > 8
                ? `${k.token.slice(0, 4)}••••••••••••${k.token.slice(-4)}`
                : '••••••••••••';
              const validation = validationResults[k.id];

              return (
                <div
                  key={k.id}
                  className={`p-3.5 rounded-2xl bg-white dark:bg-slate-900 border transition-all ${
                    k.isDefault
                      ? 'border-amber-500/40 dark:border-amber-500/30 shadow-xs'
                      : 'border-slate-200 dark:border-slate-800'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <div
                        onClick={() => onSetDefaultKey(k.id)}
                        className="cursor-pointer"
                        title={k.isDefault ? 'Active default key' : 'Click to make default'}
                      >
                        <div
                          className={`w-4 h-4 rounded-full border flex items-center justify-center transition-colors ${
                            k.isDefault
                              ? 'border-amber-500 bg-amber-500 text-slate-950'
                              : 'border-slate-300 dark:border-slate-600 hover:border-amber-500'
                          }`}
                        >
                          {k.isDefault && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                        </div>
                      </div>

                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-slate-900 dark:text-white truncate">
                            {k.name}
                          </span>
                          {k.isDefault && (
                            <span className="text-[10px] font-medium px-1.5 py-0.2 rounded-md bg-amber-500/15 text-amber-800 dark:text-amber-300 border border-amber-500/25">
                              Active
                            </span>
                          )}
                        </div>
                        <p className="text-[10px] text-slate-400 font-mono mt-0.5">
                          Added {new Date(k.createdAt).toLocaleDateString()}
                        </p>
                      </div>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => toggleVisibility(k.id)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                        title={isVisible ? 'Hide token' : 'Reveal token'}
                      >
                        {isVisible ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                      </button>

                      <button
                        onClick={() => handleCopy(k.id, k.token)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                        title="Copy token"
                      >
                        {copiedId === k.id ? (
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>

                      <button
                        onClick={() => onDeleteKey(k.id)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/30 transition-colors cursor-pointer"
                        title="Delete key"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Token string */}
                  <div className="mt-2.5 px-2.5 py-1.5 rounded-xl bg-slate-50 dark:bg-slate-800/80 border border-slate-100 dark:border-slate-800 flex items-center justify-between text-xs font-mono text-slate-600 dark:text-slate-300">
                    <span className="truncate">{isVisible ? k.token : masked}</span>
                    <button
                      onClick={() => handleValidate(k.id, k.token)}
                      disabled={validatingId === k.id}
                      className="ml-2 text-[10px] text-amber-600 dark:text-amber-400 hover:underline shrink-0 flex items-center gap-1 cursor-pointer disabled:opacity-50"
                    >
                      {validatingId === k.id ? (
                        <>
                          <Loader2 className="w-3 h-3 animate-spin" />
                          <span>Checking...</span>
                        </>
                      ) : (
                        <span>Verify</span>
                      )}
                    </button>
                  </div>

                  {/* Validation Result Badge */}
                  {validation && (
                    <div className="mt-2 pt-2 border-t border-slate-100 dark:border-slate-800 text-[11px]">
                      {validation.valid ? (
                        <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400">
                          <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                          <span>
                            Verified: <strong className="font-semibold">{validation.user?.fullname || validation.user?.name}</strong>{' '}
                            ({validation.user?.plan || 'Free'} Plan)
                          </span>
                        </div>
                      ) : (
                        <div className="flex items-center gap-1.5 text-rose-500">
                          <ShieldAlert className="w-3.5 h-3.5 shrink-0" />
                          <span>Verification failed: {validation.error}</span>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Informational Card */}
        <div className="p-3.5 rounded-2xl bg-amber-500/5 dark:bg-amber-500/10 border border-amber-500/20 text-xs text-slate-700 dark:text-slate-300 space-y-1.5">
          <h4 className="font-semibold text-slate-900 dark:text-amber-300 flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-amber-500" />
            ZeroGPU Quotas & Tokens
          </h4>
          <p className="text-[11px] leading-relaxed text-slate-600 dark:text-slate-400">
            For ZeroGPU Spaces, authenticated requests consume your personal account&apos;s daily GPU quota, while unauthenticated requests use a shared pool with stricter limits. PRO, Team, and Enterprise accounts enjoy extended daily GPU limits.
          </p>
        </div>
      </div>
    </div>
  );
};
