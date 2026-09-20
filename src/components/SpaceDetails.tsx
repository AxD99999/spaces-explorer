import React, { useState } from 'react';
import {
  ArrowLeft,
  ExternalLink,
  Heart,
  Flame,
  Cpu,
  User,
  Clock,
  Globe,
  Tag,
  Code,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
  Play,
} from 'lucide-react';
import { motion } from 'motion/react';
import { HfSpace } from '../types';
import { getSpaceType, getSpaceTypeStyle } from '../utils';

interface SpaceDetailsProps {
  space: HfSpace;
  onBack: () => void;
}

export const SpaceDetails: React.FC<SpaceDetailsProps> = ({ space, onBack }) => {
  const [copiedId, setCopiedId] = useState(false);
  const [copiedDomain, setCopiedDomain] = useState(false);
  const [copiedJson, setCopiedJson] = useState(false);
  const [showLiveEmbed, setShowLiveEmbed] = useState(false);
  const [isRawJsonExpanded, setIsRawJsonExpanded] = useState(false);

  const authorName = space.author || space.id.split('/')[0] || 'community';
  const spaceTitle = space.title || space.id.split('/')[1] || space.id;
  const emoji = space.emoji || '🤗';
  const avatarUrl = space.authorData?.avatarUrl;
  const isRunning = space.runtime?.stage === 'RUNNING';
  const stage = space.runtime?.stage || 'UNKNOWN';
  const spaceType = getSpaceType(space);
  const currentHardware = space.runtime?.hardware?.current || 'cpu-basic';
  const requestedHardware = space.runtime?.hardware?.requested || currentHardware;
  const primaryDomain = space.subdomain
    ? `${space.subdomain}.hf.space`
    : space.id.replace('/', '-') + '.hf.space';

  const copyToClipboard = (text: string, type: 'id' | 'domain' | 'json') => {
    navigator.clipboard.writeText(text);
    if (type === 'id') {
      setCopiedId(true);
      setTimeout(() => setCopiedId(false), 2000);
    } else if (type === 'domain') {
      setCopiedDomain(true);
      setTimeout(() => setCopiedDomain(false), 2000);
    } else if (type === 'json') {
      setCopiedJson(true);
      setTimeout(() => setCopiedJson(false), 2000);
    }
  };

  const formattedDate = (dateStr?: string) => {
    if (!dateStr) return 'Unknown';
    try {
      return new Date(dateStr).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  const gcHours = space.runtime?.gcTimeout ? Math.round(space.runtime.gcTimeout / 3600) : null;

  return (
    <motion.div
      id="space-details-view"
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -20 }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      className="flex-1 overflow-y-auto pb-20 scrollbar-thin bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 transition-colors"
    >
      {/* Sticky Top Header */}
      <div className="sticky top-0 z-30 bg-white/90 dark:bg-slate-900/90 backdrop-blur-md px-4 py-3 border-b border-slate-200/80 dark:border-slate-800/80 flex items-center justify-between transition-colors">
        <div className="flex items-center gap-2 min-w-0">
          <button
            id="btn-back-to-spaces"
            onClick={onBack}
            className="p-1.5 -ml-1 rounded-full text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all cursor-pointer"
            aria-label="Back to spaces"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <h2 className="text-sm font-semibold truncate leading-tight">
              {spaceTitle}
            </h2>
            <p className="text-[11px] text-amber-600 dark:text-amber-400 font-mono truncate">
              @{authorName}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <button
            id="btn-copy-space-id"
            onClick={() => copyToClipboard(space.id, 'id')}
            title="Copy Space ID"
            className="p-2 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all cursor-pointer"
            aria-label="Copy Space ID"
          >
            {copiedId ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
          </button>

          <a
            id="btn-open-hf-web"
            href={`https://huggingface.co/spaces/${space.id}`}
            target="_blank"
            rel="noopener noreferrer"
            title="View on Hugging Face"
            className="p-2 rounded-xl text-slate-500 dark:text-slate-400 hover:text-amber-600 dark:hover:text-amber-400 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all cursor-pointer"
            aria-label="Open on Hugging Face"
          >
            <ExternalLink className="w-4 h-4" />
          </a>
        </div>
      </div>

      <div className="p-4 space-y-4">
        {/* Header Hero Card */}
        <div className="p-4 rounded-2xl bg-white dark:bg-slate-800/80 border border-slate-200/80 dark:border-slate-700/80 shadow-2xs relative overflow-hidden transition-colors">
          <div className="flex items-start gap-3.5">
            <div className="w-12 h-12 rounded-xl bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-2xl shrink-0 border border-slate-200 dark:border-slate-600">
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

                <span
                  className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-semibold tracking-wider uppercase border ${
                    isRunning
                      ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20'
                      : stage === 'SLEEPING'
                      ? 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20'
                      : 'bg-slate-100 dark:bg-slate-700/60 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-600'
                  }`}
                >
                  <span
                    className={`w-1.5 h-1.5 rounded-full ${
                      isRunning ? 'bg-emerald-500 dark:bg-emerald-400' : stage === 'SLEEPING' ? 'bg-amber-500' : 'bg-slate-400'
                    }`}
                  />
                  {stage}
                </span>

                {space.sdk && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-mono font-bold bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-200 uppercase">
                    {space.sdk}
                  </span>
                )}

                {space.visibility && (
                  <span className="text-[10px] text-slate-500 dark:text-slate-400 font-mono px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700">
                    {space.visibility}
                  </span>
                )}
              </div>

              <h1 className="text-base sm:text-lg font-bold text-slate-900 dark:text-slate-100 mt-1.5 leading-snug">
                {spaceTitle}
              </h1>

              <div className="flex items-center gap-2 mt-1 text-xs text-slate-500 dark:text-slate-400">
                <span>ID:</span>
                <span className="font-mono text-slate-700 dark:text-slate-300 text-[11px] truncate max-w-[200px]">
                  {space.id}
                </span>
              </div>
            </div>
          </div>

          {/* Engagement metrics row */}
          <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-700/60 grid grid-cols-2 xs:grid-cols-4 gap-2 text-center">
            <div className="p-2 rounded-xl bg-slate-50 dark:bg-slate-900/50 border border-slate-200/80 dark:border-slate-700/50">
              <div className="text-[11px] text-slate-500 dark:text-slate-400 flex items-center justify-center gap-1 mb-0.5">
                <Heart className="w-3 h-3 text-rose-500" />
                <span>Likes</span>
              </div>
              <div className="text-sm font-bold text-slate-900 dark:text-slate-100">
                {(space.likes || 0).toLocaleString()}
              </div>
            </div>

            <div className="p-2 rounded-xl bg-slate-50 dark:bg-slate-900/50 border border-slate-200/80 dark:border-slate-700/50">
              <div className="text-[11px] text-slate-500 dark:text-slate-400 flex items-center justify-center gap-1 mb-0.5">
                <Flame className="w-3 h-3 text-amber-500" />
                <span>Trending</span>
              </div>
              <div className="text-sm font-bold text-slate-900 dark:text-slate-100">
                {space.trendingScore || 0}
              </div>
            </div>

            <div className="p-2 rounded-xl bg-slate-50 dark:bg-slate-900/50 border border-slate-200/80 dark:border-slate-700/50">
              <div className="text-[11px] text-slate-500 dark:text-slate-400 flex items-center justify-center gap-1 mb-0.5">
                <Cpu className="w-3 h-3 text-sky-500" />
                <span>Hardware</span>
              </div>
              <div className="text-xs font-mono font-bold text-slate-800 dark:text-slate-200 truncate">
                {currentHardware}
              </div>
            </div>

            <div className="p-2 rounded-xl bg-slate-50 dark:bg-slate-900/50 border border-slate-200/80 dark:border-slate-700/50">
              <div className="text-[11px] text-slate-500 dark:text-slate-400 flex items-center justify-center gap-1 mb-0.5">
                <Clock className="w-3 h-3 text-indigo-500" />
                <span>Timeout</span>
              </div>
              <div className="text-xs font-bold text-slate-800 dark:text-slate-200">
                {gcHours ? `${gcHours}h` : 'Default'}
              </div>
            </div>
          </div>
        </div>

        {/* Public Domain Launch Bar */}
        {primaryDomain && (
          <div className="p-3.5 rounded-2xl bg-indigo-50 dark:bg-indigo-950/30 border border-indigo-200/80 dark:border-indigo-500/20 flex items-center justify-between gap-2">
            <div className="min-w-0 flex-1">
              <div className="text-[11px] text-indigo-700 dark:text-indigo-300 font-medium flex items-center gap-1.5">
                <Globe className="w-3.5 h-3.5" />
                <span>App Domain:</span>
              </div>
              <div className="text-xs font-mono text-slate-800 dark:text-slate-200 truncate mt-0.5">
                https://{primaryDomain}
              </div>
            </div>

            <div className="flex items-center gap-1.5 shrink-0">
              <button
                id="btn-copy-domain"
                onClick={() => copyToClipboard(`https://${primaryDomain}`, 'domain')}
                title="Copy Domain URL"
                className="p-2 rounded-xl bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white border border-slate-200 dark:border-slate-700 active:scale-95 transition-all text-xs cursor-pointer shadow-2xs"
              >
                {copiedDomain ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
              </button>

              <a
                id="btn-launch-domain"
                href={`https://${primaryDomain}`}
                target="_blank"
                rel="noopener noreferrer"
                className="px-3 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs flex items-center gap-1 shadow-2xs active:scale-95 transition-all"
              >
                <span>Launch</span>
                <ExternalLink className="w-3 h-3" />
              </a>
            </div>
          </div>
        )}

        {/* Live Embed Toggle in Android Frame */}
        {primaryDomain && isRunning && (
          <div className="rounded-2xl border border-slate-200/80 dark:border-slate-700/80 bg-white dark:bg-slate-800/40 overflow-hidden">
            <button
              onClick={() => setShowLiveEmbed(!showLiveEmbed)}
              className="w-full px-4 py-3 flex items-center justify-between text-xs font-semibold text-slate-800 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors cursor-pointer"
            >
              <div className="flex items-center gap-2">
                <Play className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                <span>Live Space Preview</span>
                <span className="px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 text-[10px]">
                  Interactive
                </span>
              </div>
              {showLiveEmbed ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
            </button>

            {showLiveEmbed && (
              <div className="p-2 bg-slate-100 dark:bg-slate-950 border-t border-slate-200 dark:border-slate-800">
                <div className="w-full h-80 rounded-xl overflow-hidden border border-slate-200 dark:border-slate-800 bg-white">
                  <iframe
                    src={`https://${primaryDomain}`}
                    title={spaceTitle}
                    className="w-full h-full border-0"
                    sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
                  />
                </div>
                <div className="text-[10px] text-slate-500 text-center mt-1.5">
                  Rendered via {primaryDomain}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Description */}
        <div className="p-4 rounded-2xl bg-white dark:bg-slate-800/50 border border-slate-200/80 dark:border-slate-700/60 space-y-2.5">
          <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Description
          </h3>

          {space.ai_short_description && (
            <div className="p-3 rounded-xl bg-slate-50 dark:bg-amber-500/5 border border-slate-200/80 dark:border-amber-500/15">
              <div className="text-[11px] font-semibold text-amber-700 dark:text-amber-400 mb-1">
                Category: {space.ai_category || 'General AI'}
              </div>
              <p className="text-xs text-slate-700 dark:text-slate-200 leading-relaxed">
                {space.ai_short_description}
              </p>
            </div>
          )}

          {space.shortDescription && space.shortDescription !== space.ai_short_description && (
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              {space.shortDescription}
            </p>
          )}

          {!space.ai_short_description && !space.shortDescription && (
            <p className="text-xs text-slate-400 italic">
              No written description provided by creator.
            </p>
          )}
        </div>

        {/* Runtime & Infrastructure Specifications */}
        <div className="p-4 rounded-2xl bg-white dark:bg-slate-800/50 border border-slate-200/80 dark:border-slate-700/60 space-y-2.5">
          <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Infrastructure Specifications
          </h3>

          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-900/60 border border-slate-200/80 dark:border-slate-800">
              <span className="text-[11px] text-slate-500 dark:text-slate-400 block mb-0.5">Stage</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 capitalize">{stage}</span>
            </div>

            <div className="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-900/60 border border-slate-200/80 dark:border-slate-800">
              <span className="text-[11px] text-slate-500 dark:text-slate-400 block mb-0.5">Current Hardware</span>
              <span className="font-mono font-semibold text-amber-700 dark:text-amber-300">{currentHardware}</span>
            </div>

            <div className="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-900/60 border border-slate-200/80 dark:border-slate-800">
              <span className="text-[11px] text-slate-500 dark:text-slate-400 block mb-0.5">Requested Hardware</span>
              <span className="font-mono text-slate-700 dark:text-slate-300">{requestedHardware}</span>
            </div>

            <div className="p-2.5 rounded-xl bg-slate-50 dark:bg-slate-900/60 border border-slate-200/80 dark:border-slate-800">
              <span className="text-[11px] text-slate-500 dark:text-slate-400 block mb-0.5">Replicas</span>
              <span className="text-slate-700 dark:text-slate-300 font-mono">
                {space.runtime?.replicas?.current ?? 1} / {space.runtime?.replicas?.requested ?? 1}
              </span>
            </div>
          </div>
        </div>

        {/* Creator Info */}
        <div className="p-4 rounded-2xl bg-white dark:bg-slate-800/50 border border-slate-200/80 dark:border-slate-700/60 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            {avatarUrl ? (
              <img
                src={avatarUrl}
                alt={authorName}
                className="w-10 h-10 rounded-xl object-cover border border-slate-200 dark:border-slate-600 shrink-0"
                referrerPolicy="no-referrer"
              />
            ) : (
              <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-slate-700 dark:text-slate-300 font-bold shrink-0">
                {authorName[0]?.toUpperCase() || 'U'}
              </div>
            )}

            <div className="min-w-0">
              <div className="font-semibold text-slate-900 dark:text-slate-100 text-sm truncate">
                {space.authorData?.fullname || authorName}
              </div>
              <div className="text-xs text-amber-600 dark:text-amber-400 font-mono">
                @{authorName}
              </div>
            </div>
          </div>

          <a
            href={`https://huggingface.co/${authorName}`}
            target="_blank"
            rel="noopener noreferrer"
            className="p-2 rounded-xl bg-slate-100 dark:bg-slate-700/60 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 shrink-0"
            title="View creator"
          >
            <ExternalLink className="w-4 h-4" />
          </a>
        </div>

        {/* Raw JSON Inspector */}
        <div className="rounded-2xl border border-slate-200/80 dark:border-slate-700/60 bg-white dark:bg-slate-800/40 overflow-hidden">
          <button
            id="btn-toggle-raw-json"
            onClick={() => setIsRawJsonExpanded(!isRawJsonExpanded)}
            className="w-full px-4 py-3 flex items-center justify-between text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-2">
              <Code className="w-4 h-4 text-slate-500 dark:text-slate-400" />
              <span>Inspect Raw Space API JSON</span>
            </div>
            {isRawJsonExpanded ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            )}
          </button>

          {isRawJsonExpanded && (
            <div className="p-4 bg-slate-50 dark:bg-slate-950/80 border-t border-slate-200 dark:border-slate-800 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">spaces-json Payload</span>
                <button
                  id="btn-copy-raw-json"
                  onClick={() => copyToClipboard(JSON.stringify(space, null, 2), 'json')}
                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-xs text-slate-700 dark:text-slate-300 transition-colors cursor-pointer"
                >
                  {copiedJson ? <Check className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                  <span>{copiedJson ? 'Copied' : 'Copy JSON'}</span>
                </button>
              </div>
              <pre className="p-3 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-[11px] font-mono text-slate-800 dark:text-slate-300 overflow-x-auto max-h-72 scrollbar-thin">
                {JSON.stringify(space, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
};
