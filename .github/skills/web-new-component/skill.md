# Skill: Create Web Component

## Description  
Create a new React component for SafeSphere Next.js admin dashboard following exact project patterns and conventions.

## When to Use
- Adding new UI elements to admin pages
- Creating reusable dashboard components
- Building form components, data displays, modals
- Implementing new admin features

## Prerequisites
- SafeSphere Next.js project (`admin/`)
- React 19.2.3 and TypeScript understanding
- Tailwind CSS 4.x knowledge
- Component file at `admin/src/components/` or `admin/src/app/`

## Steps

### Step 1: Plan Component
Identify:
- [ ] Is this a page component (in `app/`) or reusable component (in `components/`)?
- [ ] What data does it need? (from props, Supabase, or somewhere else)
- [ ] Is it interactive? (form inputs, buttons, etc.)  
- [ ] Does it need icons? (use Lucide React)
- [ ] Does it need authentication check? (in page.tsx, not component)

### Step 2: Create Component File
**Location:** `admin/src/components/YourComponent.tsx`

**Template Pattern (from existing components):**
```typescript
'use client';

import React, { useState } from 'react';
import { ChevronRight, Shield } from 'lucide-react';

interface YourComponentProps {
  title: string;
  onAction?: () => void;
  isLoading?: boolean;
}

export function YourComponent({
  title,
  onAction,
  isLoading = false,
}: YourComponentProps) {
  const [state, setState] = useState<string>('');

  const handleSubmit = async () => {
    if (isLoading) return;
    
    try {
      // Do something
      if (onAction) onAction();
    } catch (error) {
      console.error('Error:', error);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-md p-6 border border-gray-200">
      <h2 className="text-xl font-bold text-gray-800 mb-4">{title}</h2>
      
      {/* Your JSX here */}
      
      <button
        onClick={handleSubmit}
        disabled={isLoading}
        className="w-full bg-blue-600 text-white font-semibold py-2 px-4 rounded-lg hover:bg-blue-700 disabled:opacity-50"
      >
        {isLoading ? 'Processing...' : 'Submit'}
      </button>
    </div>
  );
}
```

**Conventions found in codebase:**
- ✅ `'use client'` directive at top (required for interactivity)
- ✅ TypeScript with interfaces for props
- ✅ Default exports or named exports (consistent with existing components)
- ✅ Tailwind CSS classes for styling (no inline CSS)
- ✅ Lucide React for icons (`import { IconName } from 'lucide-react'`)
- ✅ Error handling with try-catch
- ✅ Loading states in async operations

### Step 3: Add Tailwind Styling
**Patterns from existing components (from dashboard, pages):**

**Layout containers:**
```tsx
// Card layout
<div className="bg-white rounded-lg shadow-md p-6 border border-gray-200">
  {/* content */}
</div>

// Grid layouts
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
  {/* items */}
</div>

// Flexbox
<div className="flex items-center justify-between">
  {/* items */}
</div>
```

**Button styles:**
```tsx
// Primary button
<button className="bg-blue-600 text-white hover:bg-blue-700 px-4 py-2 rounded-lg">
  Action
</button>

// Danger button
<button className="bg-red-600 text-white hover:bg-red-700 px-4 py-2 rounded-lg">
  Delete
</button>

// Secondary button  
<button className="bg-gray-200 text-gray-800 hover:bg-gray-300 px-4 py-2 rounded-lg">
  Cancel
</button>
```

**Form elements:**
```tsx
<input
  type="text"
  placeholder="Enter value"
  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
/>

<select className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
  <option>Option 1</option>
</select>
```

**Text hierarchy:**
```tsx
<h1 className="text-3xl font-bold text-gray-900">Heading 1</h1>
<h2 className="text-2xl font-bold text-gray-800">Heading 2</h2>
<h3 className="text-xl font-bold text-gray-800">Heading 3</h3>
<p className="text-gray-600">Body text</p>
<span className="text-sm text-gray-500">Small text</span>
```

### Step 4: Add Icons (Lucide React)
**Pattern from dashboard and other pages:**
```typescript
import { 
  Shield, Eye, EyeOff, AlertCircle, 
  Users, Phone, Heart, ChevronRight,
  RefreshCw, UserX, Send, Activity
} from 'lucide-react';

// Usage in JSX
<Shield className="w-6 h-6 text-blue-600" />
<Eye className="w-4 h-4 text-gray-600" />
<AlertCircle className="w-5 h-5 text-red-600" />

// Common icon sizing
className="w-6 h-6"  // Standard
className="w-4 h-4"  // Small
className="w-8 h-8"  // Large
```

**Common icons in SafeSphere dashboard:**
- `Shield` - Security/protection
- `Users` - User management
- `AlertTriangle` - Warnings/incidents
- `CheckCircle` - Success/verified
- `Activity` - Metrics/analytics
- `TrendingUp` - Charts/growth
- `Phone` - Call tracking
- `Heart` - Emergency/care
- `RefreshCw` - Refresh/reload
- `UserX` - Remove/delete user

### Step 5: Handle Supabase Data Fetch (if needed)
**Pattern for client component fetching data:**
```typescript
'use client';

import { useEffect, useState } from 'react';

interface DataType {
  id: string;
  name: string;
}

export function MyDataComponent() {
  const [data, setData] = useState<DataType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      try {
        setIsLoading(true);
        const response = await fetch('/api/admin/metrics');
        
        if (!response.ok) {
          throw new Error('Failed to fetch data');
        }
        
        const result = await response.json();
        setData(result);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error');
      } finally {
        setIsLoading(false);
      }
    }

    fetchData();
  }, []);

  if (isLoading) return <div>Loading...</div>;
  if (error) return <div className="text-red-600">Error: {error}</div>;

  return (
    <div>
      {data.map((item) => (
        <div key={item.id}>{item.name}</div>
      ))}
    </div>
  );
}
```

### Step 6: Create Page Component (if in app/)
**Pattern from `/admin/dashboard/page.tsx`, `/admin/incidents/page.tsx`:**
```typescript
// admin/src/app/admin/your-page/page.tsx

import { createClient } from '@/lib/supabase/server';
import { redirect } from 'next/navigation';

export default async function YourPage() {
  // Server-side auth check
  const supabase = await createClient();
  const { data: { user } } = await supabase.auth.getUser();

  if (!user) {
    redirect('/admin/login');
  }

  // Fetch server-side data
  const { data: admin, error } = await supabase
    .from('admin_accounts')
    .select('*')
    .eq('email', user.email)
    .single();

  if (error || !admin?.is_active) {
    redirect('/admin/login');
  }

  // Fetch page data
  const { data: items } = await supabase
    .from('your_table')
    .select('*');

  return (
    <div className="min-h-screen bg-gray-100 p-8">
      <h1 className="text-3xl font-bold mb-6">Your Page</h1>
      
      {/* Pass data to client component */}
      <YourComponent initialData={items} />
    </div>
  );
}
```

### Step 7: Write Tests (optional but recommended)
**Test pattern (Jest + React Testing Library):**
```typescript
import { render, screen } from '@testing-library/react';
import { YourComponent } from './YourComponent';

describe('YourComponent', () => {
  it('renders with title', () => {
    render(<YourComponent title="Test Title" />);
    expect(screen.getByText('Test Title')).toBeInTheDocument();
  });

  it('calls onAction when button clicked', async () => {
    const onAction = jest.fn();
    render(<YourComponent title="Test" onAction={onAction} />);
    
    const button = screen.getByRole('button');
    await button.click();
    
    expect(onAction).toHaveBeenCalled();
  });
});
```

## Verification
- [ ] TypeScript file created at `admin/src/components/YourComponent.tsx` or `admin/src/app/admin/...`
- [ ] Has `'use client'` directive if interactive
- [ ] Exports component with proper TypeScript types
- [ ] Styled with Tailwind CSS (no inline styles)
- [ ] Uses Lucide React icons (imported correctly)
- [ ] Error handling present (try-catch, error states)
- [ ] Loading states if async operations
- [ ] Props properly typed with interfaces
- [ ] Next.js build compiles: `npm run build`
- [ ] Component renders without errors in browser

## Anti-Hallucination Checks
- [ ] Lucide React icons imported from 'lucide-react' exist in library
- [ ] Tailwind CSS classes are valid (no misspelled utility classes)
- [ ] Supabase functions match actual SDK if used
- [ ] API endpoints match actual routes (`/api/admin/*`)
- [ ] All types and interfaces match actual data structures
- [ ] No reference to non-existent localStorage, window APIs outside effect hooks

## Examples

### Example 1: Simple Stat Card (Like Dashboard Metrics)
```typescript
// components/StatCard.tsx
import { ReactNode } from 'react';

interface StatCardProps {
  label: string;
  value: number | string;
  icon: ReactNode;
  trend?: number;
}

export function StatCard({ label, value, icon, trend }: StatCardProps) {
  return (
    <div className="bg-white rounded-lg shadow-md p-6 border border-gray-200">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-600 mb-1">{label}</p>
          <p className="text-3xl font-bold text-gray-900">{value}</p>
          {trend !== undefined && (
            <p className={`text-sm mt-2 ${trend > 0 ? 'text-green-600' : 'text-red-600'}`}>
              {trend > 0 ? '+' : ''}{trend}%
            </p>
          )}
        </div>
        <div className="text-blue-600">{icon}</div>
      </div>
    </div>
  );
}
```

### Example 2: User Search Component
```typescript
// components/UserSearch.tsx
'use client';

import { useState } from 'react';
import { Search } from 'lucide-react';

interface User {
  id: string;
  display_name: string;
  phone_hash: string;
}

export function UserSearch() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const handleSearch = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setQuery(value);

    if (!value.trim()) {
      setResults([]);
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch(`/api/admin/search?q=${encodeURIComponent(value)}`);
      const data = await response.json();
      setResults(data);
    } catch (error) {
      console.error('Search error:', error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="mb-6">
      <div className="relative">
        <Search className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
        <input
          type="text"
          value={query}
          onChange={handleSearch}
          placeholder="Search users by name or phone..."
          className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      
      {isLoading && <p className="mt-2 text-gray-500">Searching...</p>}
      
      <div className="mt-4 space-y-2">
        {results.map((user) => (
          <div key={user.id} className="p-3 bg-gray-50 rounded border border-gray-200">
            <p className="font-semibold">{user.display_name}</p>
            <p className="text-sm text-gray-600">{user.phone_hash}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
```

### Example 3: Modal/Dialog Component
```typescript
// components/ConfirmDialog.tsx
interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  onCancel: () => void;
  isLoading?: boolean;
}

export function ConfirmDialog({
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  onConfirm,
  onCancel,
  isLoading = false,
}: ConfirmDialogProps) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm">
        <h2 className="text-xl font-bold text-gray-900 mb-2">{title}</h2>
        <p className="text-gray-600 mb-6">{message}</p>
        
        <div className="flex gap-3">
          <button
            onClick={onCancel}
            disabled={isLoading}
            className="flex-1 bg-gray-200 text-gray-800 hover:bg-gray-300 px-4 py-2 rounded-lg disabled:opacity-50"
          >
            {cancelText}
          </button>
          <button
            onClick={onConfirm}
            disabled={isLoading}
            className="flex-1 bg-red-600 text-white hover:bg-red-700 px-4 py-2 rounded-lg disabled:opacity-50"
          >
            {isLoading ? 'Processing...' : confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
```

## Changelog
- 2026-03-30 - Initial creation
- Documented React component pattern with TypeScript
- Included Tailwind CSS styling patterns from dashboard
- Added Lucide React icon integration
- Documented page components with Supabase server auth
- Added examples for stat cards, search, modals
