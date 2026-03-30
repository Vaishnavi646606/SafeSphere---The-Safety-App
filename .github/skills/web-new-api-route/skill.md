# Skill: Create Web API Route

## Description
Create a new Next.js API route for SafeSphere admin dashboard following exact project patterns and conventions.

## When to Use
- Adding new endpoints for admin functionality
- Creating public API routes for Android app communication
- Implementing data endpoints for frontend pages
- Adding authentication-protected admin operations

## Prerequisites
- SafeSphere Next.js project (`safesphere-admin/`)
- TypeScript understanding (strict mode enabled)
- Supabase client libraries available
- Route file at `safesphere-admin/src/app/api/[route]/route.ts`

## Steps

### Step 1: Determine Route Type
Identify if route is:
- **Public:** `/api/user/register`, `/api/analytics/ingest`, `/api/revocation/check` (called by Android app)
- **Admin Protected:** `/api/admin/*` (requires Supabase user authenticated + admin_accounts check)
- **Method:** GET, POST, PUT, DELETE

### Step 2: Create Route File
**Location:** `safesphere-admin/src/app/api/[path]/route.ts`

**Basic Pattern (from existing routes):**
```typescript
import { createServiceClient } from '@/lib/supabase/server';
import { NextRequest, NextResponse } from 'next/server';

// Public endpoint example (/api/user/register)
export async function POST(request: NextRequest) {
  try {
    const { name, phone } = await request.json();
    
    // Validate input
    if (!name || !phone) {
      return NextResponse.json(
        { error: 'Missing required fields' },
        { status: 400 }
      );
    }
    
    const supabase = await createServiceClient();
    
    // Query/insert data
    const { data, error } = await supabase
      .from('users')
      .upsert(
        {
          name,
          phone_hash: hashPhone(phone),
          masked_phone: maskPhone(phone),
        },
        { onConflict: 'phone_hash' }
      )
      .select('id, revocation_version')
      .single();
    
    if (error) {
      return NextResponse.json(
        { error: error.message },
        { status: 500 }
      );
    }
    
    return NextResponse.json({
      user_id: data.id,
      revocation_version: data.revocation_version,
    });
    
  } catch (error) {
    return NextResponse.json(
      { error: 'Internal server error' },
      { status: 500 }
    );
  }
}
```

**Admin Protected Pattern (from /api/admin/* routes):**
```typescript
import { createServiceClient } from '@/lib/supabase/server';
import { createClient as createAuthClient } from '@/lib/supabase/server';
import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    // 1. Check authentication
    const authClient = await createAuthClient();
    const { data: { user }, error: authError } = await authClient.auth.getUser();
    
    if (authError || !user) {
      return NextResponse.json(
        { error: 'Unauthorized' },
        { status: 401 }
      );
    }
    
    // 2. Check admin status
    const supabase = await createServiceClient();
    const { data: admin, error: adminError } = await supabase
      .from('admin_accounts')
      .select('id, is_active')
      .eq('email', user.email)
      .single();
    
    if (adminError || !admin?.is_active) {
      return NextResponse.json(
        { error: 'Forbidden' },
        { status: 403 }
      );
    }
    
    // 3. Process request
    const body = await request.json();
    // ... implement logic
    
    return NextResponse.json({ success: true });
    
  } catch (error) {
    return NextResponse.json(
      { error: 'Internal server error' },
      { status: 500 }
    );
  }
}
```

### Step 3: Implement Supabase Queries
**Patterns from existing routes:**

**Querying data (from /api/admin/metrics):**
```typescript
const { data: users, error } = await supabase
  .from('users')
  .select('id, is_active, revoked_at')
  .is('revoked_at', null);  // NULL check pattern

const totalUsers = users?.length || 0;
const activeUsers = users?.filter(u => u.is_active)?.length || 0;
```

**Inserting/updating (from /api/user/register):**
```typescript
const { data, error } = await supabase
  .from('users')
  .upsert(
    { id: userId, name, phone_hash },
    { onConflict: 'phone_hash' }  // Handle duplicates
  )
  .select()
  .single();

if (error) throw error;
return data;
```

**Atomic transactions (from /api/admin/remove-user):**
```typescript
// 1. Update users table
await supabase
  .from('users')
  .update({
    is_active: false,
    revoked_at: new Date().toISOString(),
    revocation_reason: reason,
  })
  .eq('id', userId);

// 2. Increment revocation version
await supabase
  .from('revocation_tokens')
  .upsert({
    user_id: userId,
    revocation_version: currentVersion + 1,
    last_checked_at: new Date().toISOString(),
  });

// 3. Log to audit
await supabase
  .from('admin_actions')
  .insert({
    admin_id: adminId,
    action: 'remove_user',
    resource_type: 'user',
    resource_id: userId,
    timestamp: new Date().toISOString(),
  });
```

### Step 4: Add Input Validation
**Pattern from existing routes:**
```typescript
// Validate required fields
if (!body.name || !body.phone) {
  return NextResponse.json(
    { error: 'Missing required fields: name, phone' },
    { status: 400 }
  );
}

// Validate data types
if (typeof body.name !== 'string') {
  return NextResponse.json(
    { error: 'name must be string' },
    { status: 400 }
  );
}

// Validate formats (email, phone, etc.)
const phoneRegex = /^\d{10}$/;  // Example: 10 digit US phone
if (!phoneRegex.test(body.phone)) {
  return NextResponse.json(
    { error: 'Invalid phone format' },
    { status: 400 }
  );
}
```

### Step 5: Add Rate Limiting (if public API)
**Pattern from /api/analytics/ingest:**
```typescript
// Check rate limit (example: 10 requests per user per 60s)
const { data: recent } = await supabase
  .from('analytics_events')
  .select('id')
  .eq('user_id', userId)
  .gte('client_ts_ms', Date.now() - 60000)
  .limit(11);

if (recent && recent.length >= 10) {
  return NextResponse.json(
    { error: 'Rate limit exceeded' },
    { status: 429 }
  );
}
```

### Step 6: Add Error Handling
**Standard patterns:**
```typescript
try {
  // ... implementation
} catch (error) {
  // Log error for debugging
  console.error('Api error in /api/path:', error);
  
  // Return generic error to client
  return NextResponse.json(
    { error: 'Internal server error' },
    { status: 500 }
  );
}
```

### Step 7: Register in Route Structure
**Location verified:** `safesphere-admin/src/app/api/[path]/route.ts`

**Confirmed routes structure from codebase:**
- `src/app/api/user/register/route.ts` ✅
- `src/app/api/revocation/check/route.ts` ✅
- `src/app/api/analytics/ingest/route.ts` ✅
- `src/app/api/admin/metrics/route.ts` ✅
- `src/app/api/admin/incidents/route.ts` ✅
- And 5 more admin routes...

## Verification
- [ ] TypeScript file created at `safesphere-admin/src/app/api/[path]/route.ts`
- [ ] Exports `POST`, `GET`, or other HTTP method function
- [ ] Properly typed with NextRequest → NextResponse
- [ ] Supabase client created with `createServiceClient()` (backend) or auth client
- [ ] Input validation present (non-empty fields, data types)
- [ ] Error handling with try-catch
- [ ] Returns JSON with appropriate status codes (200, 400, 401, 403, 500)
- [ ] Admin routes check authentication + admin_accounts table
- [ ] Public routes have rate limiting if needed
- [ ] Package.json dependencies verified (supabase modules exist)
- [ ] Next.js build compiles without errors: `npm run build`
- [ ] API endpoint callable via `fetch()` from frontend or Android

## Anti-Hallucination Checks
- [ ] Supabase tables exist: `users`, `admin_accounts`, `analytics_events`, `incidents`, etc.
- [ ] Supabase functions match actual SDK (createServiceClient, createClient imported from real paths)
- [ ] Column names match database schema (phone_hash, revoked_at, is_active, etc.)
- [ ] NextResponse/NextRequest from 'next/server' is standard Next.js
- [ ] Error messages and status codes are HTTP standard
- [ ] All referenced tables verified to exist in Supabase schema

## Examples

### Example 1: Public User Registration API
```typescript
// /api/user/register/route.ts (from actual codebase)
import { createServiceClient } from '@/lib/supabase/server';
import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';

const PHONE_HASH_SALT = process.env.PHONE_HASH_SALT || 'salt';

function hashPhone(phone: string): string {
  return crypto.createHash('sha256').update(phone + PHONE_HASH_SALT).digest('hex');
}

export async function POST(request: NextRequest) {
  try {
    const { name, phone } = await request.json();
    
    if (!name?.trim() || !phone?.trim()) {
      return NextResponse.json(
        { error: 'Name and phone required' },
        { status: 400 }
      );
    }
    
    const supabase = await createServiceClient();
    const phoneHash = hashPhone(phone);
    const maskedPhone = phone.slice(0, 3) + 'XXXX' + phone.slice(-2);
    
    const { data, error } = await supabase
      .from('users')
      .upsert({
        name,
        phone_hash: phoneHash,
        masked_phone: maskedPhone,
      }, { onConflict: 'phone_hash' })
      .select('id, revocation_version')
      .single();
    
    if (error) throw error;
    
    return NextResponse.json({
      user_id: data.id,
      revocation_version: data.revocation_version || 0,
    });
  } catch (error) {
    console.error('Register error:', error);
    return NextResponse.json({ error: 'Server error' }, { status: 500 });
  }
}
```

### Example 2: Admin-Protected Metrics Endpoint
```typescript
// /api/admin/metrics/route.ts (from actual codebase pattern)
import { createServiceClient } from '@/lib/supabase/server';
import { createClient as createAuthClient } from '@/lib/supabase/server';
import { NextRequest, NextResponse } from 'next/server';

export async function GET(request: NextRequest) {
  try {
    // Check auth
    const authClient = await createAuthClient();
    const { data: { user }, error: authError } = await authClient.auth.getUser();
    
    if (!user) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    
    // Check admin
    const supabase = await createServiceClient();
    const { data: admin } = await supabase
      .from('admin_accounts')
      .select('id')
      .eq('email', user.email)
      .eq('is_active', true)
      .single();
    
    if (!admin) {
      return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }
    
    // Fetch metrics
    const { data: totalUsers } = await supabase
      .from('users')
      .select('id');
    
    const { data: activeUsers } = await supabase
      .from('users')
      .select('id')
      .eq('is_active', true)
      .is('revoked_at', null);
    
    return NextResponse.json({
      total_users: totalUsers?.length || 0,
      active_users: activeUsers?.length || 0,
    });
  } catch (error) {
    return NextResponse.json({ error: 'Server error' }, { status: 500 });
  }
}
```

### Example 3: Admin Action with Audit Logging
```typescript
// /api/admin/remove-user/route.ts pattern
export async function POST(request: NextRequest) {
  try {
    // Auth check
    const authClient = await createAuthClient();
    const { data: { user } } = await authClient.auth.getUser();
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    
    const { user_id, reason } = await request.json();
    if (!user_id) {
      return NextResponse.json({ error: 'user_id required' }, { status: 400 });
    }
    
    const supabase = await createServiceClient();
    
    // Get current revocation version
    const { data: current } = await supabase
      .from('revocation_tokens')
      .select('revocation_version')
      .eq('user_id', user_id)
      .single();
    
    const nextVersion = (current?.revocation_version || 0) + 1;
    
    // Update user
    await supabase
      .from('users')
      .update({
        is_active: false,
        revoked_at: new Date().toISOString(),
        revocation_reason: reason,
      })
      .eq('id', user_id);
    
    // Update revocation version
    await supabase
      .from('revocation_tokens')
      .upsert({
        user_id,
        revocation_version: nextVersion,
      });
    
    // Audit log
    await supabase
      .from('admin_actions')
      .insert({
        admin_id: user.id,
        action: 'remove_user',
        resource_type: 'user',
        resource_id: user_id,
      });
    
    return NextResponse.json({ success: true });
  } catch (error) {
    return NextResponse.json({ error: 'Server error' }, { status: 500 });
  }
}
```

## Changelog
- 2026-03-30 - Initial creation
- Documented public endpoint pattern (user/register, analytics/ingest, revocation/check)
- Documented admin protected pattern with auth + admin checks
- Documented Supabase query patterns (select, upsert, atomicity, audit logs)
- Added input validation, rate limiting, error handling examples
