"""Offline regression checks against the actual Room SQL (no production database)."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[4]
source = (root / "app/src/main/kotlin/com/jagr/fridamusic/db/DatabaseDao.kt").read_text(encoding="utf-8")

# Single and multiline Room annotations; no generated SQL copies.
def query(method):
    end = source.index("fun " + method + "(")
    start = source.rfind("@Query(", 0, end)
    annotation = source[start:end]
    delimiter = '"""' if annotation.lstrip().startswith('@Query(\n') else '"'
    first = annotation.index(delimiter)
    return annotation[first + len(delimiter):annotation.index(delimiter, first + len(delimiter))]

con = sqlite3.connect(":memory:")
con.row_factory = sqlite3.Row
con.executescript("""
CREATE TABLE artist(id TEXT PRIMARY KEY, name TEXT, bookmarkedAt INT);
CREATE TABLE song(id TEXT PRIMARY KEY, inLibrary INT);
CREATE TABLE song_artist_map(songId TEXT, artistId TEXT, PRIMARY KEY(songId,artistId));
INSERT INTO artist VALUES ('UCa','Alpha',1),('UCb','Beta',2),('UCc','Gamma',3);
INSERT INTO song VALUES ('s1',1),('s2',1),('s3',NULL);
INSERT INTO song_artist_map VALUES ('s1','UCb'),('s2','UCb'),('s3','UCa');
CREATE TABLE album(id TEXT PRIMARY KEY, title TEXT, year INT, songCount INT, isUploaded INT, bookmarkedAt INT, duration INT);
INSERT INTO album VALUES ('a','Beta',2020,10,1,NULL,1),('b','Alpha',2021,5,1,NULL,2),('c','Favorite',2022,12,0,1,3);
CREATE TABLE playlist(id TEXT PRIMARY KEY, name TEXT, browseId TEXT, isLocal INT, remoteSongCount INT);
INSERT INTO playlist VALUES ('p','User rename','remote',0,NULL),('local','Local','remote',1,NULL);
""")
artists = con.execute(query("artistsBookmarkedBySongCountAsc")).fetchall()
assert {r["id"]: r["songCount"] for r in artists} == {"UCa": 0, "UCb": 2, "UCc": 0}
assert [r["songCount"] for r in artists] == [0,0,2]
assert [r["id"] for r in con.execute(query("artistsBookmarkedByNameAsc"))] == ["UCa","UCb","UCc"]
for method in ["albumsUploadedByCreateDateAsc", "albumsUploadedByNameAsc", "albumsUploadedByYearAsc", "albumsUploadedBySongCountAsc"]:
    rows = con.execute(query(method)).fetchall()
    assert {r["id"] for r in rows} == {"a", "b"}, method
    assert len(rows) == 2, method
assert [r["id"] for r in con.execute(query("albumsUploadedByNameAsc"))] == ["b","a"]
assert [r["year"] for r in con.execute(query("albumsUploadedByYearAsc"))] == [2020,2021]
update = query("updateRemotePlaylistSongCount")
con.execute(update, {"browseId":"remote", "count":15})
assert con.execute("SELECT remoteSongCount, name FROM playlist WHERE id='p'").fetchone()[:] == (15,"User rename")
assert con.execute("SELECT remoteSongCount FROM playlist WHERE id='local'").fetchone()[0] is None
assert con.execute(update, {"browseId":"remote", "count":15}).rowcount == 0
con.execute(update, {"browseId":"remote", "count":0})
assert con.execute("SELECT remoteSongCount FROM playlist WHERE id='p'").fetchone()[0] == 0
print("PASS: actual Room count/filter/order SQL, no duplicates, targeted idempotent count persistence")
