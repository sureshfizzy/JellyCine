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
