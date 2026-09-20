import React, { useState, useEffect } from 'react';
import {
  ArrowLeft,
  ExternalLink,
  Heart,
  Flame,
  Cpu,
  Play,
  Copy,
  Check,
  Code,
  Sparkles,
  Terminal,
  ShieldCheck,
  FileJson,
  Layers,
  ChevronRight,
  Loader2,
} from 'lucide-react';
import { motion } from 'motion/react';
import { HfSpace, SpaceEndpointSpec } from '../types';
import { getSpaceType, getSpaceTypeStyle } from '../utils';
import { fetchSpaceInfo } from '../services/api';

interface SpaceDetailsProps {
  space: HfSpace;
  onBack: () => void;
  onRunSpace?: (space: HfSpace) => void;
  onOpenApiKeys?: () => void;
}

export const SpaceDetails: React.FC<SpaceDetailsProps> = ({
  space,
  onBack,
  onRunSpace,
  onOpenApiKeys,
}) => {
  const [activeCodeTab, setActiveCodeTab] = useState<'python' | 'js' | 'curl'>('python');
  const [copiedCode, setCopiedCode] = useState(false);
  const [copiedEndpoint, setCopiedEndpoint] = useState(false);
  const [copiedId, setCopiedId] = useState(false);
  const [isLoadingSpec, setIsLoadingSpec] = useState(true);
  const [endpoints, setEndpoints] = useState<SpaceEndpointSpec[]>([]);
  const [subdomain, setSubdomain] = useState<string>(
    space.subdomain || space.id.replace('/', '-').toLowerCase()
  );

  const authorName = space.author || space.id.split('/')[0] || 'community';
  const spaceTitle = space.title || space.id.split('/')[1] || space.id;
  const emoji = space.emoji || '🤗';
  const spaceType = getSpaceType(space);
  const currentHardware = space.runtime?.hardware?.current || 'cpu-basic';
  const likesCount = typeof space.likes === 'number' ? space.likes : 0;
  const trendingScore = typeof space.trendingScore === 'number' ? space.trendingScore : 0;
  const isGpu =
    currentHardware.toLowerCase().includes('a10') ||
    currentHardware.toLowerCase().includes('gpu') ||
    currentHardware.toLowerCase().includes('t4') ||
    currentHardware.toLowerCase().includes('zero');

  const hostUrl = `https://${subdomain}.hf.space`;
  const openApiUrl = `${hostUrl}/gradio_api/openapi.json`;

  useEffect(() => {
    let isMounted = true;
    setIsLoadingSpec(true);

    fetchSpaceInfo(space.id)
      .then((res) => {
        if (!isMounted) return;
        if (res.subdomain) setSubdomain(res.subdomain);
        if (res.endpoints && res.endpoints.length > 0) {
          setEndpoints(res.endpoints);
        }
      })
      .catch((err) => {
        console.warn('Failed to load live space spec:', err);
      })
      .finally(() => {
        if (isMounted) setIsLoadingSpec(false);
      });

    return () => {
      isMounted = false;
    };
  }, [space.id]);

  const primaryEndpoint = endpoints.length > 0 ? endpoints[0].name : '/predict';

  const copyText = (text: string, type: 'code' | 'endpoint' | 'id') => {
    navigator.clipboard.writeText(text);
    if (type === 'code') {
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2000);
    } else if (type === 'endpoint') {
      setCopiedEndpoint(true);
      setTimeout(() => setCopiedEndpoint(false), 2000);
    } else if (type === 'id') {
      setCopiedId(true);
      setTimeout(() => setCopiedId(false), 2000);
    }
  };

  const pythonSnippet = `# pip install --upgrade gradio_client
from gradio_client import Client, handle_file

# Connect to space as an API
client = Client("${space.id}")
# For ZeroGPU / private access:
# client = Client("${space.id}", hf_token="hf_...")

result = client.predict(
    api_name="${primaryEndpoint}"
)
print(result)`;

  const jsSnippet = `// npm install @gradio/client
import { Client } from "@gradio/client";

// Connect to Space API endpoint
const app = await Client.connect("${space.id}");
// For private or ZeroGPU quota:
// const app = await Client.connect("${space.id}", { hf_token: "hf_..." });

const result = await app.predict("${primaryEndpoint}", []);
console.log(result.data);`;

  const curlSnippet = `# Call Gradio Queue API
curl -X POST "${hostUrl}/gradio_api/call${primaryEndpoint}" \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer $HF_TOKEN" \\
  -d '{"data": []}'

# Read event stream at:
# GET ${hostUrl}/gradio_api/call${primaryEndpoint}/<event_id>`;

  const activeSnippet =
    activeCodeTab === 'python'
      ? pythonSnippet
      : activeCodeTab === 'js'
      ? jsSnippet
      : curlSnippet;

  return (
    <motion.div
      id="space-details-view"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 8 }}
      transition={{ duration: 0.18 }}
      className="flex-1 flex flex-col h-full bg-slate-50 dark:bg-slate-950 overflow-hidden"
    >
      {/* Top Header */}
      <header className="px-4 py-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between shrink-0 shadow-xs">
        <div className="flex items-center gap-2.5 min-w-0">
          <button
            onClick={onBack}
            className="p-1.5 -ml-1 rounded-lg text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            aria-label="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white truncate">
              {spaceTitle}
            </h2>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 font-mono truncate">
              @{authorName}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          <button
            onClick={() => copyText(space.id, 'id')}
            className="p-1.5 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="Copy space ID"
          >
            {copiedId ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
          </button>

          <a
            href={`https://huggingface.co/spaces/${space.id}`}
            target="_blank"
            rel="noopener noreferrer"
            className="p-1.5 rounded-lg text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer"
            title="Open on Hugging Face"
          >
            <ExternalLink className="w-4 h-4" />
          </a>

          {onRunSpace && (
            <button
              onClick={() => onRunSpace(space)}
              className="ml-1 flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-600 text-slate-950 font-semibold text-xs transition-colors cursor-pointer shadow-xs"
            >
              <Play className="w-3 h-3 fill-slate-950" />
              <span>Use Space</span>
            </button>
          )}
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 max-w-3xl mx-auto w-full">
        {/* Space Overview Card */}
        <div className="p-4 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-3">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-start gap-3 min-w-0 flex-1">
              <div className="w-11 h-11 rounded-2xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-2xl shrink-0">
                <span>{emoji}</span>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium tracking-tight border ${getSpaceTypeStyle(
                      spaceType
                    )}`}
                  >
                    {spaceType}
                  </span>

                  {space.sdk && (
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-mono uppercase bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
                      {space.sdk}
                    </span>
                  )}

                  <span
                    className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-mono border ${
                      isGpu
                        ? 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/20'
                        : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-200 dark:border-slate-700'
                    }`}
                  >
                    <Cpu className="w-2.5 h-2.5" />
                    <span>{currentHardware}</span>
                  </span>
                </div>

                <h1 className="text-base font-semibold text-slate-900 dark:text-white mt-1.5">
                  {spaceTitle}
                </h1>
              </div>
            </div>

            <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400 shrink-0">
              {trendingScore > 0 && (
                <span className="inline-flex items-center gap-0.5 text-amber-600 dark:text-amber-400 font-medium">
                  <Flame className="w-3.5 h-3.5 fill-amber-500/20" />
                  <span>{trendingScore}</span>
                </span>
              )}
              <span className="inline-flex items-center gap-0.5 text-rose-600 dark:text-rose-400 font-medium">
                <Heart className="w-3.5 h-3.5 fill-rose-500/20" />
                <span>{likesCount.toLocaleString()}</span>
              </span>
            </div>
          </div>

          <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
            {space.ai_short_description || space.shortDescription || 'No description provided for this space.'}
          </p>

          <div className="pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 font-mono">
            <span className="truncate">Host: {subdomain}.hf.space</span>
            <a
              href={openApiUrl}
              target="_blank"
              rel="noreferrer"
              className="text-amber-600 dark:text-amber-400 hover:underline flex items-center gap-1 shrink-0"
            >
              <FileJson className="w-3 h-3" />
              <span>OpenAPI JSON</span>
            </a>
          </div>
        </div>

        {/* Spaces as API Endpoints Card */}
        <div className="p-4 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200/90 dark:border-slate-800 shadow-xs space-y-3.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Code className="w-4 h-4 text-amber-500" />
              <h3 className="text-xs font-semibold text-slate-900 dark:text-white">
                Spaces as API Endpoints
              </h3>
            </div>
            <a
              href="https://huggingface.co/docs/hub/en/spaces-api-endpoints"
              target="_blank"
              rel="noreferrer"
              className="text-[11px] text-slate-400 hover:text-amber-600 dark:hover:text-amber-400 flex items-center gap-1"
            >
              <span>HF Hub Docs</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          </div>

          <p className="text-[11px] text-slate-600 dark:text-slate-400 leading-relaxed">
            Every Gradio space automatically exposes programmatic API endpoints. You can invoke this space from Python with <code className="px-1 py-0.2 bg-slate-100 dark:bg-slate-800 rounded font-mono text-[10px]">gradio_client</code>, JavaScript with <code className="px-1 py-0.2 bg-slate-100 dark:bg-slate-800 rounded font-mono text-[10px]">@gradio/client</code>, or via standard HTTP/cURL requests.
          </p>

          {/* Endpoints Discovery List */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-[11px] text-slate-500 font-medium">
              <span>Discovered Endpoints</span>
              {isLoadingSpec && (
                <span className="flex items-center gap-1 text-[10px] text-amber-500">
                  <Loader2 className="w-3 h-3 animate-spin" /> Querying space schema...
                </span>
              )}
            </div>

            {endpoints.length > 0 ? (
              <div className="space-y-2">
                {endpoints.map((ep) => (
                  <div
                    key={ep.name}
                    className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200/70 dark:border-slate-700/60 space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-mono font-semibold text-amber-600 dark:text-amber-400">
                        {ep.name}
                      </span>
                      <span className="text-[10px] font-mono text-slate-400">
                        {ep.parameters?.length || 0} inputs &bull; {ep.returns?.length || 0} outputs
                      </span>
                    </div>

                    {/* Parameters summary */}
                    {ep.parameters && ep.parameters.length > 0 && (
                      <div className="pt-1.5 border-t border-slate-200/60 dark:border-slate-700/60 grid grid-cols-1 sm:grid-cols-2 gap-1.5 text-[11px]">
                        {ep.parameters.slice(0, 6).map((param, idx) => (
                          <div
                            key={idx}
                            className="flex items-center justify-between px-2 py-1 rounded-lg bg-white dark:bg-slate-900/80 border border-slate-100 dark:border-slate-800"
                          >
                            <span className="text-slate-700 dark:text-slate-300 font-mono text-[10px] truncate max-w-[120px]">
                              {param.parameter_name || param.label || `param_${idx}`}
                            </span>
                            <span className="text-[9px] text-slate-400 font-mono">
                              {param.component || param.python_type?.type || 'string'}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : !isLoadingSpec ? (
              <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-800 text-xs text-slate-500 font-mono">
                Primary API Endpoint: <span className="text-amber-500 font-semibold">/predict</span>
              </div>
            ) : null}
          </div>

          {/* Code Integration Snippets */}
          <div className="pt-2">
            <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-2">
              {/* Language Tabs */}
              <div className="flex items-center gap-1">
                {(['python', 'js', 'curl'] as const).map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setActiveCodeTab(tab)}
                    className={`px-2.5 py-1 rounded-lg text-xs font-medium uppercase tracking-wider transition-colors cursor-pointer ${
                      activeCodeTab === tab
                        ? 'bg-amber-500/15 text-amber-900 dark:text-amber-300 font-semibold'
                        : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
                    }`}
                  >
                    {tab === 'js' ? 'JavaScript' : tab}
                  </button>
                ))}
              </div>

              <button
                onClick={() => copyText(activeSnippet, 'code')}
                className="flex items-center gap-1 text-[11px] text-slate-500 hover:text-slate-900 dark:hover:text-white cursor-pointer"
              >
                {copiedCode ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-500" />
                    <span className="text-emerald-500">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copy Code</span>
                  </>
                )}
              </button>
            </div>

            <div className="mt-2.5 rounded-xl bg-slate-950 p-3 text-[11px] font-mono text-slate-200 overflow-x-auto border border-slate-800">
              <pre className="leading-relaxed whitespace-pre font-mono">{activeSnippet}</pre>
            </div>
          </div>
        </div>

        {/* Authentication & ZeroGPU Quota Card */}
        <div className="p-4 rounded-2xl bg-amber-500/5 dark:bg-amber-500/10 border border-amber-500/20 shadow-xs space-y-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-amber-600 dark:text-amber-400" />
              <h3 className="text-xs font-semibold text-slate-900 dark:text-amber-300">
                Authentication & ZeroGPU Quota
              </h3>
            </div>
            {onOpenApiKeys && (
              <button
                onClick={onOpenApiKeys}
                className="text-[11px] font-semibold text-amber-700 dark:text-amber-400 hover:underline flex items-center gap-1 cursor-pointer"
              >
                <span>Manage API Keys</span>
                <ChevronRight className="w-3 h-3" />
              </button>
            )}
          </div>

          <p className="text-[11px] text-slate-600 dark:text-slate-400 leading-relaxed">
            Authenticated requests using a Hugging Face user access token consume your account&apos;s GPU quota rather than the strict anonymous public pool. PRO, Team, and Enterprise users enjoy expanded daily GPU quotas and faster execution priority.
          </p>
        </div>
      </div>
    </motion.div>
  );
};
