'use client'

import React from 'react'

interface LoadingSkeletonProps {
  type?: 'card' | 'line' | 'table' | 'list'
  count?: number
  height?: string
  width?: string
  className?: string
}

export default function LoadingSkeleton({
  type = 'card',
  count = 1,
  height = 'h-12',
  width = 'w-full',
  className = ''
}: LoadingSkeletonProps) {
  if (type === 'card') {
    return (
      <div className={`grid gap-6 md:grid-cols-2 lg:grid-cols-4 ${className}`}>
        {Array(count)
          .fill(0)
          .map((_, i) => (
            <div
              key={i}
              className="min-h-42.5 rounded-2xl border border-white/5 bg-[#12121a] p-6"
            >
              <div className="space-y-4">
                <div className="h-3 w-1/3 animate-pulse rounded-xl bg-white/5" />
                <div className="h-9 w-1/2 animate-pulse rounded-xl bg-white/5" />
                <div className="h-3 w-1/4 animate-pulse rounded-xl bg-white/5" />
              </div>
            </div>
          ))}
      </div>
    )
  }

  if (type === 'line') {
    return (
      <div className={`space-y-3 ${className}`}>
        {Array(count)
          .fill(0)
          .map((_, i) => (
            <div
              key={i}
              className={`${height} ${width} animate-pulse rounded-xl bg-white/5`}
            />
          ))}
      </div>
    )
  }

  if (type === 'table') {
    return (
      <div className={`space-y-3 ${className}`}>
        {/* Header */}
        <div className="flex gap-4">
          {Array(4)
            .fill(0)
            .map((_, i) => (
              <div
                key={i}
                className="h-4 flex-1 animate-pulse rounded-xl bg-white/5"
              />
            ))}
        </div>
        {/* Rows */}
        {Array(count)
          .fill(0)
          .map((_, i) => (
            <div key={i} className="flex gap-4">
              {Array(4)
                .fill(0)
                .map((_, j) => (
                  <div
                    key={j}
                    className="h-10 flex-1 animate-pulse rounded-xl bg-white/5"
                  />
                ))}
            </div>
          ))}
      </div>
    )
  }

  if (type === 'list') {
    return (
      <div className={`space-y-3 ${className}`}>
        {Array(count)
          .fill(0)
          .map((_, i) => (
            <div
              key={i}
              className="flex items-center gap-4 rounded-2xl border border-white/5 bg-[#12121a] p-4"
            >
              <div className="h-10 w-10 animate-pulse rounded-xl bg-white/5" />
              <div className="flex-1 space-y-2">
                <div className="h-4 w-3/4 animate-pulse rounded-xl bg-white/5" />
                <div className="h-3 w-1/2 animate-pulse rounded-xl bg-white/5" />
              </div>
            </div>
          ))}
      </div>
    )
  }

  return null
}

