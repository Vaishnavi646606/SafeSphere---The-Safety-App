'use client'

import { useEffect, useState, useCallback } from 'react'
import { useParams } from 'next/navigation'
import { MapPin, RefreshCw, AlertCircle, Clock } from 'lucide-react'

interface LiveLocationData {
  lat: number
  lng: number
  accuracy: number
  last_updated: string
  display_name: string
  is_active: boolean
  found: boolean
}

export default function TrackingPage() {
  const params = useParams()
  const token = params.token as string
  const REFRESH_INTERVAL_MS = 180000

  const [data, setData] = useState<LiveLocationData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date())
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [mapLoaded, setMapLoaded] = useState(false)

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
        setData(json)
        setError(null)
        setLastRefresh(new Date())
      }
    } catch {
      setError('Network error — check your connection')
    } finally {
      setLoading(false)
      if (isManual) setIsRefreshing(false)
    }
  }, [token])

  useEffect(() => { fetchLocation() }, [fetchLocation])

  useEffect(() => {
    const interval = setInterval(() => fetchLocation(), REFRESH_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [fetchLocation, REFRESH_INTERVAL_MS])

  useEffect(() => {
    if (!data || !data.found || !data.is_active || mapLoaded) return
    const L = (window as any).L
    if (L) { initMap(L, data.lat, data.lng); setMapLoaded(true); return }
    if (!document.getElementById('leaflet-css')) {
      const link = document.createElement('link')
      link.id = 'leaflet-css'
      link.rel = 'stylesheet'
      link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'
      document.head.appendChild(link)
    }
    const script = document.createElement('script')
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    script.onload = () => { initMap((window as any).L, data.lat, data.lng); setMapLoaded(true) }
    document.head.appendChild(script)
  }, [data, mapLoaded])

  useEffect(() => {
    if (!data || !mapLoaded || !data.found) return
    const map = (window as any)._safesphereMap
    const marker = (window as any)._safesphereMarker
    const L = (window as any).L
    if (map && marker && L) {
      const newLatLng = L.latLng(data.lat, data.lng)
      marker.setLatLng(newLatLng)
      map.panTo(newLatLng)
    }
  }, [data, mapLoaded])

  const initMap = (L: any, lat: number, lng: number) => {
    const existing = (window as any)._safesphereMap
    if (existing) { existing.remove(); (window as any)._safesphereMap = null; (window as any)._safesphereMarker = null }
    const map = L.map('safesphere-map').setView([lat, lng], 16)
    ;(window as any)._safesphereMap = map
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors', maxZoom: 19,
    }).addTo(map)
    const icon = L.divIcon({
      className: '',
      html: `<div style="width:20px;height:20px;background:#ef4444;border:3px solid #fff;border-radius:50%;box-shadow:0 0 0 3px #ef444466;"></div>`,
      iconSize: [20, 20], iconAnchor: [10, 10],
    })
    const marker = L.marker([lat, lng], { icon }).addTo(map)
    ;(window as any)._safesphereMarker = marker
  }

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
            <span style={{ color:'#94a3b8', fontSize:'14px' }}>Updated {ageText}</span>
          </div>
          <div style={{ fontSize:'12px', color:statusColor, background:`${statusColor}18`, padding:'4px 10px', borderRadius:'20px', border:`1px solid ${statusColor}40` }}>
            {statusColor === '#10b981' ? 'Live' : statusColor === '#f59e0b' ? 'Recent' : 'Delayed'}
          </div>
        </div>

        <div style={{ background:'#111219', border:'1px solid rgba(255,255,255,0.06)', borderRadius:'14px', padding:'16px' }}>
          <div style={{ display:'flex', alignItems:'center', gap:'8px', marginBottom:'10px' }}>
            <MapPin size={16} color="#10b981" />
            <span style={{ color:'#94a3b8', fontSize:'13px', fontWeight:600 }}>COORDINATES</span>
          </div>
          <p style={{ margin:'0 0 4px', fontSize:'15px', fontWeight:600 }}>{data.lat.toFixed(6)}, {data.lng.toFixed(6)}</p>
          {data.accuracy > 0 && <p style={{ margin:0, fontSize:'12px', color:'#64748b' }}>Accuracy: ±{Math.round(data.accuracy)}m</p>}
        </div>

        <a href={mapsUrl} target="_blank" rel="noopener noreferrer"
          style={{ display:'block', background:'#10b981', color:'#fff', textAlign:'center', padding:'14px', borderRadius:'14px', textDecoration:'none', fontWeight:600, fontSize:'15px' }}>
          📍 Open in Google Maps
        </a>

        <p style={{ textAlign:'center', color:'#475569', fontSize:'12px', margin:'4px 0 0' }}>
          Auto-refreshes every 3 minutes · Last refresh: {lastRefresh.toLocaleTimeString()}
        </p>

        <div style={{ textAlign:'center', padding:'16px', borderTop:'1px solid rgba(255,255,255,0.04)', marginTop:'8px' }}>
          <p style={{ margin:0, color:'#334155', fontSize:'12px' }}>🔒 Powered by SafeSphere — Women Safety App</p>
        </div>
      </div>
      <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
    </div>
  )
}
