import React from 'react';
import {
  Heart,
  Flame,
  Pin,
  Star,
  Info,
  Play,
} from 'lucide-react';
import { HfSpace } from '../types';
import { getSpaceType, getSpaceTypeStyle } from '../utils';

interface SpaceCardProps {
  space: HfSpace;
  isFavourite?: boolean;
  onSelect: (space: HfSpace) => void;
  onToggleFavourite?: (space: HfSpace) => void;
  onOpenDetails?: (space: HfSpace) => void;
}

export const SpaceCard: React.FC<SpaceCardProps> = ({
  space,
  isFavourite = false,
  onSelect,
  onToggleFavourite,
  onOpenDetails,
}) => {
  const authorName = space.author || space.id.split('/')[0] || 'community';
  const spaceTitle = space.title || space.id.split('/')[1] || space.id;
  const avatarUrl = space.authorData?.avatarUrl;
  const emoji = space.emoji || '🤗';
  const likesCount = typeof space.likes === 'number' ? space.likes : 0;
  const trendingScore = typeof space.trendingScore === 'number' ? space.trendingScore : 0;
  const description = space.ai_short_description || space.shortDescription || 'No description provided.';
  const spaceType = getSpaceType(space);

  return (
    <div
      id={`space-card-${space.id.replace(/[^a-zA-Z0-9_-]/g, '_')}`}
      onClick={() => onSelect(space)}
      className="group relative p-3.5 rounded-2xl bg-white dark:bg-slate-800/60 hover:bg-slate-50/80 dark:hover:bg-slate-800 border border-slate-200/80 dark:border-slate-700/60 hover:border-amber-500/40 dark:hover:border-amber-500/30 transition-all cursor-pointer shadow-2xs hover:shadow-sm flex flex-col justify-between"
    >
      <div>
        {/* Top row: Emoji, Author, Title, Space Type, Favourite Button */}
        <div className="flex items-start justify-between gap-2.5">
          <div className="flex items-center gap-2.5 min-w-0 flex-1">
            {/* Space Emoji Icon */}
            <div className="w-9 h-9 rounded-xl bg-slate-100 dark:bg-slate-700/70 border border-slate-200 dark:border-slate-600/70 flex items-center justify-center text-lg shrink-0 select-none shadow-2xs group-hover:scale-105 transition-transform">
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
                <span className="text-[11px] font-mono text-slate-500 dark:text-slate-400 font-medium truncate max-w-[120px]">
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

          {/* Top Actions: Space Type Badge & Favourite Button */}
          <div className="flex items-center gap-1.5 shrink-0">
            <span
              className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium tracking-tight border whitespace-nowrap ${getSpaceTypeStyle(
                spaceType
              )}`}
            >
              {spaceType}
            </span>

            {/* Favourite Button */}
            <button
              id={`btn-fav-${space.id.replace(/[^a-zA-Z0-9_-]/g, '_')}`}
              onClick={(e) => {
                e.stopPropagation();
                onToggleFavourite?.(space);
              }}
              className={`p-1.5 rounded-xl border transition-all cursor-pointer ${
                isFavourite
                  ? 'bg-amber-500/15 border-amber-500/35 text-amber-500 shadow-xs'
                  : 'bg-slate-100/80 dark:bg-slate-700/60 border-slate-200/80 dark:border-slate-700 text-slate-400 hover:text-amber-500 hover:bg-amber-500/10 dark:hover:bg-amber-500/10'
              }`}
              aria-label={isFavourite ? 'Remove from favourites' : 'Save to favourites'}
              title={isFavourite ? 'Remove from favourites' : 'Save to favourites'}
            >
              <Star
                className={`w-3.5 h-3.5 transition-transform group-hover:scale-105 ${
                  isFavourite ? 'fill-amber-500 text-amber-500' : ''
                }`}
              />
            </button>
          </div>
        </div>

        {/* Description */}
        <p className="text-xs text-slate-600 dark:text-slate-300 line-clamp-2 mt-2 leading-relaxed font-normal">
          {description}
        </p>
      </div>

      {/* Bottom Metadata: Numbers on the left, buttons on the right */}
      <div className="mt-2.5 pt-2 border-t border-slate-100 dark:border-slate-700/50 flex items-center justify-between gap-2">
        {/* Left side: Likes & Trending numbers */}
        <div className="flex items-center gap-2.5 text-xs text-slate-500 dark:text-slate-400">
          {trendingScore > 0 && (
            <span className="inline-flex items-center gap-1 text-[11px] text-amber-600 dark:text-amber-400 font-medium">
              <Flame className="w-3 h-3 fill-amber-500/20" />
              <span>{trendingScore}</span>
            </span>
          )}
          <span className="inline-flex items-center gap-1 text-[11px] text-rose-600 dark:text-rose-400 font-medium">
            <Heart className="w-3 h-3 fill-rose-500/20" />
            <span>{likesCount.toLocaleString()}</span>
          </span>
        </div>

        {/* Right side: Click to Run indicator and Details button */}
        <div className="flex items-center gap-1.5 shrink-0">
          <span className="inline-flex items-center gap-1 text-[10px] font-medium text-amber-600 dark:text-amber-400 bg-amber-500/10 dark:bg-amber-500/15 px-2 py-0.5 rounded-md border border-amber-500/20 opacity-90 group-hover:opacity-100 transition-opacity">
            <Play className="w-2.5 h-2.5 fill-amber-600 dark:fill-amber-400" />
            <span>Run</span>
          </span>

          {/* Small Details button on bottom right */}
          <button
            id={`btn-details-${space.id.replace(/[^a-zA-Z0-9_-]/g, '_')}`}
            onClick={(e) => {
              e.stopPropagation();
              onOpenDetails?.(space);
            }}
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-medium border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-700/50 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 hover:text-slate-900 dark:hover:text-white transition-all cursor-pointer shadow-2xs"
            title="View Space Details & API Documentation"
          >
            <Info className="w-2.5 h-2.5 text-slate-400" />
            <span>Details</span>
          </button>
        </div>
      </div>
    </div>
  );
};

