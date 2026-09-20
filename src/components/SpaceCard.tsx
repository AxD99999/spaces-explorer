import React from 'react';
import {
  Heart,
  Flame,
  Cpu,
  ChevronRight,
  Pin,
} from 'lucide-react';
import { HfSpace } from '../types';
import { getSpaceType, getSpaceTypeStyle } from '../utils';

interface SpaceCardProps {
  space: HfSpace;
  onSelect: (space: HfSpace) => void;
}

export const SpaceCard: React.FC<SpaceCardProps> = ({ space, onSelect }) => {
  const currentHardware = space.runtime?.hardware?.current || 'cpu-basic';
  const authorName = space.author || space.id.split('/')[0] || 'community';
  const spaceTitle = space.title || space.id.split('/')[1] || space.id;
  const avatarUrl = space.authorData?.avatarUrl;
  const emoji = space.emoji || '🤗';
  const likesCount = typeof space.likes === 'number' ? space.likes : 0;
  const trendingScore = typeof space.trendingScore === 'number' ? space.trendingScore : 0;
  const description = space.ai_short_description || space.shortDescription || 'No description provided.';
  const spaceType = getSpaceType(space);

  // SDK color classes
  const getSdkBadgeColor = (sdk?: string) => {
    switch (sdk?.toLowerCase()) {
      case 'gradio':
        return 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/20';
      case 'docker':
        return 'bg-sky-500/10 text-sky-700 dark:text-sky-300 border-sky-500/20';
      case 'streamlit':
        return 'bg-rose-500/10 text-rose-700 dark:text-rose-300 border-rose-500/20';
      case 'static':
        return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/20';
      default:
        return 'bg-slate-100 dark:bg-slate-700/50 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700';
    }
  };

  const isGpu =
    currentHardware.toLowerCase().includes('a10') ||
    currentHardware.toLowerCase().includes('gpu') ||
    currentHardware.toLowerCase().includes('t4') ||
    currentHardware.toLowerCase().includes('h100');

  return (
    <div
      id={`space-card-${space.id.replace(/[^a-zA-Z0-9_-]/g, '_')}`}
      onClick={() => onSelect(space)}
      className="group relative p-3.5 rounded-2xl bg-white dark:bg-slate-800/60 hover:bg-slate-50/80 dark:hover:bg-slate-800 border border-slate-200/80 dark:border-slate-700/60 hover:border-slate-300 dark:hover:border-slate-600 transition-all cursor-pointer shadow-2xs hover:shadow-sm"
    >
      {/* Top row: Emoji, Author, Title, Status */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5 min-w-0 flex-1">
          {/* Space Emoji Icon */}
          <div className="w-9 h-9 rounded-xl bg-slate-100 dark:bg-slate-700/70 border border-slate-200 dark:border-slate-600/70 flex items-center justify-center text-lg shrink-0 select-none shadow-2xs">
            <span>{emoji}</span>
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5 flex-wrap">
              {avatarUrl ? (
                <img
                  src={avatarUrl}
                  alt={authorName}
                  className="w-3.5 h-3.5 rounded-full object-cover border border-slate-300 dark:border-slate-600"
                  referrerPolicy="no-referrer"
                  onError={(e) => {
                    (e.target as HTMLElement).style.display = 'none';
                  }}
                />
              ) : null}
              <span className="text-[11px] font-mono text-slate-500 dark:text-slate-400 font-medium truncate max-w-[130px]">
                @{authorName}
              </span>

              {space.pinned && (
                <span className="inline-flex items-center gap-0.5 text-[9px] text-amber-700 dark:text-amber-300 font-semibold px-1.5 py-0.2 rounded bg-amber-500/10 border border-amber-500/20">
                  <Pin className="w-2.5 h-2.5" /> Pinned
                </span>
              )}
            </div>

            {/* Space Title */}
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100 group-hover:text-amber-600 dark:group-hover:text-amber-400 transition-colors truncate mt-0.5 leading-snug">
              {spaceTitle}
            </h3>
          </div>
        </div>

        {/* Space Type Badge (moved from bottom, replacing 'running' status) */}
        <div className="shrink-0">
          <span
            className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium tracking-tight border whitespace-nowrap ${getSpaceTypeStyle(
              spaceType
            )}`}
          >
            {spaceType}
          </span>
        </div>
      </div>

      {/* Description */}
      <p className="text-xs text-slate-600 dark:text-slate-300 line-clamp-2 mt-2 leading-relaxed font-normal">
        {description}
      </p>

      {/* Bottom Metadata: SDK, Hardware & Likes */}
      <div className="mt-2.5 pt-2 border-t border-slate-100 dark:border-slate-700/50 flex items-center justify-between gap-2 flex-wrap">
        <div className="flex items-center gap-1.5 flex-wrap">
          {space.sdk && (
            <span
              className={`px-1.5 py-0.5 rounded text-[10px] font-medium border uppercase tracking-wider ${getSdkBadgeColor(
                space.sdk
              )}`}
            >
              {space.sdk}
            </span>
          )}

          <span
            className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-mono border ${
              isGpu
                ? 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/20'
                : 'bg-slate-100 dark:bg-slate-700/40 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-700'
            }`}
          >
            <Cpu className="w-2.5 h-2.5" />
            <span>{currentHardware}</span>
          </span>
        </div>

        {/* Likes and Trending */}
        <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400 ml-auto shrink-0">
          {trendingScore > 0 && (
            <span className="inline-flex items-center gap-0.5 text-[11px] text-amber-600 dark:text-amber-400 font-medium">
              <Flame className="w-3 h-3 fill-amber-500/20" />
              <span>{trendingScore}</span>
            </span>
          )}
          <span className="inline-flex items-center gap-0.5 text-[11px] text-rose-600 dark:text-rose-400 font-medium">
            <Heart className="w-3 h-3 fill-rose-500/20" />
            <span>{likesCount.toLocaleString()}</span>
          </span>
          <ChevronRight className="w-3.5 h-3.5 text-slate-400 group-hover:text-slate-700 dark:group-hover:text-slate-200 group-hover:translate-x-0.5 transition-all" />
        </div>
      </div>
    </div>
  );
};
