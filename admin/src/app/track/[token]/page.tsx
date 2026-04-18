'use client'

import { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { useParams } from 'next/navigation'
import { MapPin, RefreshCw, AlertCircle, Clock, Navigation } from 'lucide-react'

interface LiveLocationData {
  lat: number
  lng: number
  accuracy: number
  last_updated: string
  display_name: string
  is_active: boolean
  found: boolean
  helper_contact_slot?: number
  helper_contact_number?: string
  helper_lat?: number | null
  helper_lng?: number | null
  helper_accuracy?: number | null
  helper_last_updated?: string | null
  helper_distance_m?: number | null
  within_rescue_radius?: boolean
  auto_rescue_triggered?: boolean
  auto_rescue_at?: string | null
}

type HelperPermissionState = 'idle' | 'requesting' | 'granted' | 'denied' | 'unsupported'

const REFRESH_INTERVAL_MS = 180000
const RESCUE_RADIUS_METERS = 50

export default function TrackingPage() {
  const params = useParams()
  const token = params.token as string

  const [data, setData] = useState<LiveLocationData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [leafletReady, setLeafletReady] = useState(false)
  const [helperPermission, setHelperPermission] = useState<HelperPermissionState>('idle')
  const [helperSyncError, setHelperSyncError] = useState<string | null>(null)
  const helperWatchRef = useRef<number | null>(null)

  const getAgeText = (isoString: string): string => {
    const diffMs = Date.now() - new Date(isoString).getTime()
    const diffSec = Math.floor(diffMs / 1000)
    if (diffSec < 60) return `${diffSec}s ago`
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return `${diffMin} min ago`
    const diffHr = Math.floor(diffMin / 60)
    return `${diffHr} hr ago`
  }

  const getStatusColor = (isoString: string): string => {
    const diffMs = Date.now() - new Date(isoString).getTime()
    const diffMin = diffMs / 60000
    if (diffMin < 5) return '#10b981'
    if (diffMin < 15) return '#f59e0b'
    return '#ef4444'
  }

  const getPermissionText = (value: HelperPermissionState): string => {
    if (value === 'requesting') return 'Requesting helper location permission...'
    if (value === 'granted') return 'Helper live location sharing is active'
    if (value === 'denied') return 'Location permission denied by helper'
    if (value === 'unsupported') return 'Geolocation is not supported on this browser'
    return 'Waiting to request helper location'
  }

  const getDistanceText = (distanceMeters: number): string => {
    if (!Number.isFinite(distanceMeters)) return 'Unknown distance'
    if (distanceMeters < 1000) return `${Math.round(distanceMeters)}m`
    return `${(distanceMeters / 1000).toFixed(2)}km`
  }

  const fetchLocation = useCallback(async (isManual = false) => {
    if (!token) return
    if (isManual) setIsRefreshing(true)
    try {
      const res = await fetch(`/api/track/${token}`, { cache: 'no-store' })
      const json = await res.json()
      if (!res.ok) {
        setError(json.error || 'Failed to load location')
        setData(null)
      } else {
        setData(json as LiveLocationData)
        setError(null)
      }
    } catch {
      setError('Network error — check your connection')
    } finally {
      setLoading(false)
      if (isManual) setIsRefreshing(false)
    }
  }, [token])

  const syncHelperLocation = useCallback(async (lat: number, lng: number, accuracy: number) => {
    if (!token) return

    try {
      const response = await fetch(`/api/track/${token}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lat, lng, accuracy }),
      })

      const payload = await response.json()
      if (!response.ok) {
        setHelperSyncError(payload?.error || 'Could not sync helper location')
        return
      }

      setHelperSyncError(null)
      setData(payload as LiveLocationData)
    } catch {
      setHelperSyncError('Could not sync helper location')
    }
  }, [token])

  useEffect(() => { fetchLocation() }, [fetchLocation])

  useEffect(() => {
    const interval = setInterval(() => fetchLocation(), REFRESH_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [fetchLocation])

  useEffect(() => {
    if (typeof window === 'undefined') return

    const L = (window as any).L
    if (L) {
      setLeafletReady(true)
      return
    }

    if (!document.getElementById('leaflet-css')) {
      const link = document.createElement('link')
      link.id = 'leaflet-css'
      link.rel = 'stylesheet'
      link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'
      document.head.appendChild(link)
    }

    if (document.getElementById('leaflet-js')) return

    const script = document.createElement('script')
    script.id = 'leaflet-js'
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    script.onload = () => setLeafletReady(true)
    document.head.appendChild(script)
  }, [])

  const initOrUpdateMap = useCallback((payload: LiveLocationData) => {
    if (typeof window === 'undefined') return

    const L = (window as any).L
    if (!L) return

    let map = (window as any)._safesphereMap
    if (!map) {
      map = L.map('safesphere-map').setView([payload.lat, payload.lng], 16)
      ;(window as any)._safesphereMap = map
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors',
        maxZoom: 19,
      }).addTo(map)
    }

    const victimLatLng = L.latLng(payload.lat, payload.lng)
    const victimIcon = L.divIcon({
      className: '',
      html: '<div style="width:20px;height:20px;background:#ef4444;border:3px solid #fff;border-radius:50%;box-shadow:0 0 0 3px #ef444466;"></div>',
      iconSize: [20, 20],
      iconAnchor: [10, 10],
    })

    let victimMarker = (window as any)._safesphereVictimMarker
    if (!victimMarker) {
      victimMarker = L.marker(victimLatLng, { icon: victimIcon }).addTo(map)
      ;(window as any)._safesphereVictimMarker = victimMarker
    } else {
      victimMarker.setLatLng(victimLatLng)
    }

    let rescueCircle = (window as any)._safesphereRescueCircle
    if (!rescueCircle) {
      rescueCircle = L.circle(victimLatLng, {
        radius: RESCUE_RADIUS_METERS,
        color: '#22c55e',
        weight: 1,
        fillColor: '#22c55e',
        fillOpacity: 0.1,
      }).addTo(map)
      ;(window as any)._safesphereRescueCircle = rescueCircle
    } else {
      rescueCircle.setLatLng(victimLatLng)
    }

    const helperLat = payload.helper_lat
    const helperLng = payload.helper_lng
    const hasHelperMarker = typeof helperLat === 'number' && typeof helperLng === 'number'

    let helperMarker = (window as any)._safesphereHelperMarker
    let helperLine = (window as any)._safesphereHelperLine

    if (hasHelperMarker) {
      const helperLatLng = L.latLng(helperLat as number, helperLng as number)
      const helperIcon = L.divIcon({
        className: '',
        html: '<div style="width:18px;height:18px;background:#3b82f6;border:3px solid #fff;border-radius:50%;box-shadow:0 0 0 3px #3b82f666;"></div>',
        iconSize: [18, 18],
        iconAnchor: [9, 9],
      })

      if (!helperMarker) {
        helperMarker = L.marker(helperLatLng, { icon: helperIcon }).addTo(map)
        ;(window as any)._safesphereHelperMarker = helperMarker
      } else {
        helperMarker.setLatLng(helperLatLng)
      }

      if (!helperLine) {
        helperLine = L.polyline([victimLatLng, helperLatLng], {
          color: '#60a5fa',
          weight: 2,
          dashArray: '6, 6',
        }).addTo(map)
        ;(window as any)._safesphereHelperLine = helperLine
      } else {
        helperLine.setLatLngs([victimLatLng, helperLatLng])
      }

      const bounds = L.latLngBounds([victimLatLng, helperLatLng])
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 17 })
    } else {
      if (helperMarker) {
        map.removeLayer(helperMarker)
        ;(window as any)._safesphereHelperMarker = null
      }
      if (helperLine) {
        map.removeLayer(helperLine)
        ;(window as any)._safesphereHelperLine = null
      }
      map.setView(victimLatLng, 16)
    }
  }, [])

  useEffect(() => {
    if (!leafletReady || !data || !data.found || !data.is_active) return
    initOrUpdateMap(data)
  }, [leafletReady, data, initOrUpdateMap])

  useEffect(() => {
    const isHelperLink = Boolean(data?.helper_contact_slot)
    if (!isHelperLink || !token) return

    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setHelperPermission('unsupported')
      return
    }

    setHelperPermission((prev) => (prev === 'granted' ? prev : 'requesting'))

    const onSuccess = (position: GeolocationPosition) => {
      setHelperPermission('granted')
      setHelperSyncError(null)
      syncHelperLocation(
        position.coords.latitude,
        position.coords.longitude,
        position.coords.accuracy || 0
      )
    }

    const onError = (geoError: GeolocationPositionError) => {
      if (geoError.code === geoError.PERMISSION_DENIED) {
        setHelperPermission('denied')
        setHelperSyncError('Location permission denied by helper.')
      } else if (geoError.code === geoError.POSITION_UNAVAILABLE) {
        setHelperSyncError('Helper location unavailable right now.')
      } else {
        setHelperSyncError('Helper location request timed out.')
      }
    }

    navigator.geolocation.getCurrentPosition(onSuccess, onError, {
      enableHighAccuracy: true,
      timeout: 12000,
      maximumAge: 0,
    })

    if (helperWatchRef.current !== null) {
      navigator.geolocation.clearWatch(helperWatchRef.current)
    }

    helperWatchRef.current = navigator.geolocation.watchPosition(onSuccess, onError, {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 5000,
    })

    return () => {
      if (helperWatchRef.current !== null) {
        navigator.geolocation.clearWatch(helperWatchRef.current)
        helperWatchRef.current = null
      }
    }
  }, [data?.helper_contact_slot, token, syncHelperLocation])

  const isHelperLink = useMemo(() => Boolean(data?.helper_contact_slot), [data?.helper_contact_slot])

  if (loading) {
    return (
      <div style={{ minHeight:'100vh', background:'#08090e', display:'flex', alignItems:'center', justifyContent:'center', flexDirection:'column', gap:'16px', fontFamily:'system-ui,sans-serif' }}>
        <div style={{ width:'40px', height:'40px', border:'3px solid #1e2030', borderTop:'3px solid #10b981', borderRadius:'50%', animation:'spin 1s linear infinite' }} />
        <p style={{ color:'#64748b', fontSize:'14px' }}>Loading location...</p>
        <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
      </div>
    )
  }

  if (error || !data || !data.found) {
    return (
      <div style={{ minHeight:'100vh', background:'#08090e', display:'flex', alignItems:'center', justifyContent:'center', flexDirection:'column', gap:'16px', padding:'24px', fontFamily:'system-ui,sans-serif' }}>
        <div style={{ background:'#111219', border:'1px solid rgba(239,68,68,0.2)', borderRadius:'16px', padding:'32px', textAlign:'center', maxWidth:'400px', width:'100%' }}>
          <AlertCircle size={48} color="#ef4444" style={{ marginBottom:'16px' }} />
          <h2 style={{ color:'#f1f5f9', fontSize:'20px', fontWeight:700, margin:'0 0 8px' }}>Location Not Found</h2>
          <p style={{ color:'#94a3b8', fontSize:'14px', margin:'0 0 20px' }}>{error || 'This tracking link is invalid or has expired.'}</p>
          <div style={{ background:'rgba(239,68,68,0.08)', borderRadius:'12px', padding:'12px', fontSize:'13px', color:'#94a3b8' }}>🔒 SafeSphere — Women Safety App</div>
        </div>
      </div>
    )
  }

  const statusColor = getStatusColor(data.last_updated)
  const ageText = getAgeText(data.last_updated)
  const mapsUrl = `https://maps.google.com/?q=${data.lat},${data.lng}`
  const helperDistanceText =
    typeof data.helper_distance_m === 'number'
      ? getDistanceText(data.helper_distance_m)
      : null

  return (
    <div style={{ minHeight:'100vh', background:'#08090e', fontFamily:'system-ui,sans-serif', color:'#f1f5f9' }}>
      <div style={{ background:'#111219', borderBottom:'1px solid rgba(255,255,255,0.06)', padding:'16px 20px', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div style={{ display:'flex', alignItems:'center', gap:'10px' }}>
          <div style={{ width:'10px', height:'10px', borderRadius:'50%', background:statusColor, boxShadow:`0 0 8px ${statusColor}`, flexShrink:0 }} />
          <div>
            <p style={{ margin:0, fontWeight:700, fontSize:'16px' }}>{data.display_name}</p>
            <p style={{ margin:0, fontSize:'12px', color:'#64748b' }}>🔴 SafeSphere Live Tracking</p>
          </div>
        </div>
        <button onClick={() => fetchLocation(true)} disabled={isRefreshing}
          style={{ background:'rgba(255,255,255,0.05)', border:'1px solid rgba(255,255,255,0.08)', borderRadius:'10px', padding:'8px 12px', color:'#94a3b8', cursor:isRefreshing?'not-allowed':'pointer', display:'flex', alignItems:'center', gap:'6px', fontSize:'13px' }}>
          <RefreshCw size={14} style={{ animation:isRefreshing?'spin 1s linear infinite':'none' }} />
          Refresh
        </button>
      </div>

      <div id="safesphere-map" style={{ width:'100%', height:'60vh', background:'#0c0d13' }} />

      <div style={{ padding:'20px', display:'flex', flexDirection:'column', gap:'12px' }}>
        <div style={{ background:'#111219', border:'1px solid rgba(255,255,255,0.06)', borderRadius:'14px', padding:'16px', display:'flex', justifyContent:'space-between', alignItems:'center' }}>
          <div style={{ display:'flex', alignItems:'center', gap:'8px' }}>
            <Clock size={16} color="#64748b" />
            <span style={{ color:'#94a3b8', fontSize:'14px' }}>Location reported {ageText}</span>
          </div>
          <div style={{ fontSize:'12px', color:statusColor, background:`${statusColor}18`, padding:'4px 10px', borderRadius:'20px', border:`1px solid ${statusColor}40` }}>
            {statusColor === '#10b981' ? 'Live' : statusColor === '#f59e0b' ? 'Recent' : 'Delayed'}
          </div>
        </div>

        {isHelperLink && (
          <div style={{ background:'#111219', border:'1px solid rgba(255,255,255,0.06)', borderRadius:'14px', padding:'16px' }}>
            <div style={{ display:'flex', alignItems:'center', gap:'8px', marginBottom:'8px' }}>
              <Navigation size={16} color="#60a5fa" />
              <span style={{ color:'#cbd5e1', fontSize:'13px', fontWeight:600 }}>HELPER LIVE SHARING</span>
            </div>
            <p style={{ margin:'0 0 6px', fontSize:'13px', color:'#94a3b8' }}>{getPermissionText(helperPermission)}</p>
            {helperSyncError && <p style={{ margin:'0 0 6px', fontSize:'12px', color:'#f87171' }}>{helperSyncError}</p>}
            {helperDistanceText && (
              <p style={{ margin:'0 0 6px', fontSize:'13px', color:'#cbd5e1' }}>
                Distance to user: <strong>{helperDistanceText}</strong>
              </p>
            )}
            {typeof data.helper_lat === 'number' && typeof data.helper_lng === 'number' && (
              <p style={{ margin:0, fontSize:'12px', color:'#64748b' }}>
                Helper location: {data.helper_lat.toFixed(6)}, {data.helper_lng.toFixed(6)}
              </p>
            )}
            {data.auto_rescue_triggered ? (
              <p style={{ margin:'8px 0 0', fontSize:'13px', color:'#22c55e', fontWeight:600 }}>
                Rescue auto-confirmed: helper is within {RESCUE_RADIUS_METERS}m.
              </p>
            ) : data.within_rescue_radius ? (
              <p style={{ margin:'8px 0 0', fontSize:'13px', color:'#22c55e', fontWeight:600 }}>
                Helper is now within rescue radius ({RESCUE_RADIUS_METERS}m).
              </p>
            ) : null}
          </div>
        )}

        <div style={{ background:'#111219', border:'1px solid rgba(255,255,255,0.06)', borderRadius:'14px', padding:'16px' }}>
          <div style={{ display:'flex', alignItems:'center', gap:'8px', marginBottom:'10px' }}>
            <MapPin size={16} color="#10b981" />
            <span style={{ color:'#94a3b8', fontSize:'13px', fontWeight:600 }}>COORDINATES</span>
          </div>
          <p style={{ margin:'0 0 4px', fontSize:'15px', fontWeight:600 }}>{data.lat.toFixed(6)}, {data.lng.toFixed(6)}</p>
          {data.accuracy > 0 && <p style={{ margin:0, fontSize:'12px', color:'#64748b' }}>Accuracy: ±{Math.round(data.accuracy)}m</p>}
          {isHelperLink && (
            <p style={{ margin:'8px 0 0', fontSize:'12px', color:'#64748b' }}>
              Red marker: user location · Blue marker: helper location · Green circle: {RESCUE_RADIUS_METERS}m rescue radius
            </p>
          )}
        </div>

        <a href={mapsUrl} target="_blank" rel="noopener noreferrer"
          style={{ display:'block', background:'#10b981', color:'#fff', textAlign:'center', padding:'14px', borderRadius:'14px', textDecoration:'none', fontWeight:600, fontSize:'15px' }}>
          📍 Open in Google Maps
        </a>

        <p style={{ textAlign:'center', color:'#475569', fontSize:'12px', margin:'4px 0 0' }}>
          Auto-refreshes every 3 minutes · Last refresh: {ageText}
        </p>

        <div style={{ textAlign:'center', padding:'16px', borderTop:'1px solid rgba(255,255,255,0.04)', marginTop:'8px' }}>
          <p style={{ margin:0, color:'#334155', fontSize:'12px' }}>🔒 Powered by SafeSphere — Women Safety App</p>
        </div>
      </div>
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  )
}
