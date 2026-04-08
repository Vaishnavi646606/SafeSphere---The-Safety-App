# QUICK REF — UI/UX Review
# Read full skill only when doing UI review

When to use: explicit UI review request only
Last change: 2026-03-30

# Skill: UI/UX Review

## Description
Review all pages for UI/UX issues including layout problems,
auth state mismatches, responsive design, and user flow errors.

## When to Use
- After creating or modifying any page
- Before deploying to production
- When something "looks wrong"

## Checklist
1. Auth State
   - [ ] Login page shows NO authenticated UI (no sidebar, no user info)
   - [ ] Protected pages redirect to login if not authenticated
   - [ ] Already-logged-in users skip login page
   - [ ] Logout actually clears session and redirects

2. Layout
   - [ ] Sidebar only shows on authenticated pages
   - [ ] Active page is highlighted in sidebar
   - [ ] Responsive on mobile (sidebar collapses)
   - [ ] No layout shift on page load

3. Visual Consistency
   - [ ] Dark theme is consistent across all pages
   - [ ] Same spacing, border radius, colors everywhere
   - [ ] Loading states present for all data fetches
   - [ ] Error states with retry buttons
   - [ ] Empty states with helpful messages

4. User Flow
   - [ ] Login -> Dashboard redirect works
   - [ ] All sidebar links go to correct pages
   - [ ] Back buttons work
   - [ ] No dead-end pages

5. Data Display
   - [ ] Tables handle empty data gracefully
   - [ ] Charts show "No data" instead of crashing
   - [ ] Numbers format correctly (commas, decimals)
   - [ ] Dates show in readable format
   - [ ] Long text truncates with tooltip

## Anti-Hallucination
- [ ] All components imported from correct paths
- [ ] All API endpoints referenced actually exist
- [ ] Color classes are valid Tailwind classes

## Changelog
- 2026-04-08 - Initial creation - Login page sidebar fix
