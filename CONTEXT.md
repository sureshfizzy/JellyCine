# Vela

Phone client for Emby and Jellyfin. One saved account is a user on a media server; that server can have multiple lines.

## Language

**Saved Server**:
A logged-in Emby or Jellyfin account persisted in DataStore. Identity is server id plus user, not URL.
_Avoid_: Room account, connection profile

**Note**:
Optional display alias entered when adding or editing a Saved Server. When present it replaces the server-reported name in lists and headers.
_Avoid_: remark, nickname, display name, comment

**Line**:
One reachable base URL for the same Saved Server (LAN, WAN, or named backup). Token stays on the Saved Server id.
_Avoid_: endpoint, route, address alias

**Active Line**:
The Line currently used as `serverUrl` for API and playback.

**STRM original path**:
Per Saved Server preference. When on, a strm MediaSource whose Path is an http(s) URL is played at that URL instead of the server stream endpoint.
_Avoid_: direct play, direct stream, bypass transcode

**Catalog Title**:
A movie or series identity from TMDB or Douban. It is not a library item. It may match zero or more library items via provider id.
_Avoid_: Discover item, Seerr title, recommendation

**Search Source**:
A selectable origin for a search query: a Saved Server, TMDB, or Douban.
_Avoid_: search engine, catalog, provider

**Subscription**:
A series the user follows for air-date reminders. Source is Bangumi or MoviePilot, not a Jellyfin favorite.
_Avoid_: follow, watchlist, favorite

**MoviePilot Connection**:
Optional self-hosted MoviePilot URL used to aggregate subscriptions and calendars. Not a Saved Server.
_Avoid_: media server, Seerr connection

**Trakt Scrobble**:
Sending playback start, pause, and stop to Trakt. Distinct from Jellyfin session reporting.
_Avoid_: playback report, watch history

**App Update**:
A GitHub Release compared against the installed version name. It is not a Play Store listing.
_Avoid_: Play update, in-app purchase, sidecar

**Update Asset**:
A signed APK attached to an App Update, identified by flavor (phone or tv) and ABI.
_Avoid_: package, binary, artifact

**Download Mirror**:
A URL prefix applied to GitHub API and asset URLs: origin GitHub, or a gh-proxy style CDN.
_Avoid_: VPN, proxy server, cache
