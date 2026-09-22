import React, { useState } from 'react';
import { Star, Search, ArrowLeft, Sparkles, Compass, X } from 'lucide-react';
import { HfSpace } from '../types';
import { SpaceCard } from './SpaceCard';

interface FavouritesViewProps {
  favouriteSpaces: HfSpace[];
  isPhoneFrame?: boolean;
  onToggleFavourite: (space: HfSpace) => void;
  onRunSpace: (space: HfSpace) => void;
  onOpenDetails: (space: HfSpace) => void;
  onBackToBrowse: () => void;
}

export const FavouritesView: React.FC<FavouritesViewProps> = ({
  favouriteSpaces,
  isPhoneFrame = false,
  onToggleFavourite,
  onRunSpace,
  onOpenDetails,
  onBackToBrowse,
}) => {
  const [searchQuery, setSearchQuery] = useState('');

  const filtered = favouriteSpaces.filter((space) => {
    if (!searchQuery.trim()) return true;
    const query = searchQuery.toLowerCase();
    const title = (space.title || '').toLowerCase();
    const id = space.id.toLowerCase();
    const author = (space.author || '').toLowerCase();
    const desc = (space.ai_short_description || space.shortDescription || '').toLowerCase();
    return title.includes(query) || id.includes(query) || author.includes(query) || desc.includes(query);
  });

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-50 dark:bg-slate-950 overflow-hidden">
      {/* Header */}
      <header className="px-4 py-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between shrink-0 shadow-xs">
        <div className="flex items-center gap-2.5 min-w-0">
          <button
            onClick={onBackToBrowse}
            className="p-1.5 -ml-1 rounded-lg text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer shrink-0"
            aria-label="Back to browse"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-white flex items-center gap-2">
              <Star className="w-4 h-4 text-amber-500 fill-amber-500 shrink-0" />
              <span>Favourites</span>
            </h2>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate">
              {favouriteSpaces.length} saved {favouriteSpaces.length === 1 ? 'space' : 'spaces'}
            </p>
          </div>
        </div>
      </header>

      {/* Dedicated Search Row */}
      {favouriteSpaces.length > 0 && (
        <div className="px-4 py-2.5 bg-white/80 dark:bg-slate-900/80 backdrop-blur-xs border-b border-slate-200/70 dark:border-slate-800/70 shrink-0">
          <div className="relative w-full">
            <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
            <input
              type="text"
              placeholder="Search favourites..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8.5 pr-8 py-1.5 text-xs rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-hidden focus:ring-1.5 focus:ring-amber-500/40 focus:border-amber-500 transition-all shadow-2xs"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-0.5 cursor-pointer"
                aria-label="Clear search"
              >
                <X className="w-3 h-3" />
              </button>
            )}
          </div>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {favouriteSpaces.length === 0 ? (
          <div className="py-20 text-center space-y-3 max-w-sm mx-auto">
            <div className="w-12 h-12 rounded-2xl bg-amber-500/10 text-amber-500 flex items-center justify-center mx-auto">
              <Star className="w-6 h-6 stroke-[1.5]" />
            </div>
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
              No Favourites Yet
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
              Click the star icon beside any space card to pin your favorite models and spaces here for instant access.
            </p>
            <div className="pt-2">
              <button
                onClick={onBackToBrowse}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 dark:bg-white text-white dark:text-slate-950 text-xs font-semibold hover:opacity-90 transition-opacity cursor-pointer shadow-xs"
              >
                <Compass className="w-4 h-4" />
                <span>Explore Spaces</span>
              </button>
            </div>
          </div>
        ) : filtered.length === 0 ? (
          <div className="py-16 text-center text-xs text-slate-500">
            No favourite spaces match &ldquo;{searchQuery}&rdquo;
          </div>
        ) : (
          <div className={isPhoneFrame ? 'flex flex-col space-y-2.5' : 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3'}>
            {filtered.map((space) => (
              <SpaceCard
                key={space.id}
                space={space}
                isFavourite={true}
                onSelect={() => onRunSpace(space)}
                onToggleFavourite={() => onToggleFavourite(space)}
                onOpenDetails={() => onOpenDetails(space)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
