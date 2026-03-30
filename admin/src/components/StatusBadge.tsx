'use client'

import React from 'react'
import { Check, Clock, AlertCircle, XCircle, HelpCircle } from 'lucide-react'

type Status = 'success' | 'pending' | 'warning' | 'error' | 'neutral'

interface StatusBadgeProps {
  status: Status
  label: string
  size?: 'sm' | 'md' | 'lg'
  icon?: boolean
  className?: string
}

const statusConfig: Record<Status, { bg: string; text: string; icon: React.ElementType }> = {
  success: {
    bg: 'bg-emerald-500/10 border-emerald-500/20',
    text: 'text-emerald-400',
    icon: Check
  },
  pending: {
    bg: 'bg-orange-500/10 border-orange-500/20',
    text: 'text-amber-400',
    icon: Clock
  },
  warning: {
    bg: 'bg-amber-500/10 border-amber-500/20',
    text: 'text-orange-400',
    icon: AlertCircle
  },
  error: {
    bg: 'bg-rose-500/10 border-rose-500/20',
    text: 'text-rose-400',
    icon: XCircle
  },
  neutral: {
    bg: 'bg-gray-500/10 border-gray-500/20',
    text: 'text-gray-400',
    icon: HelpCircle
  }
}

const sizeConfig = {
  sm: 'px-2.5 py-1 text-xs',
  md: 'px-3 py-1.5 text-sm',
  lg: 'px-4 py-2 text-base'
}

export default function StatusBadge({
  status,
  label,
  size = 'md',
  icon = true,
  className = ''
}: StatusBadgeProps) {
  const config = statusConfig[status]
  const Icon = config.icon

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-xl border font-medium transition-all duration-200 ${
        config.bg
      } ${config.text} ${sizeConfig[size]} ${className}`}
    >
      {icon && <Icon size={size === 'sm' ? 14 : size === 'md' ? 16 : 18} />}
      {label}
    </span>
  )
}
