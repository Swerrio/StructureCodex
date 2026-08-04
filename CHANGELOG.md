### Added
- A "Terrain adaptation" option for placement. With it on, the ground around a structure is carved and levelled the way world generation does it: ancient cities come out walkable instead of solid, and villages sit on the ground instead of on the treetops. Off by default — it erases whatever was already inside the structure's footprint, including your own builds.

### Fixed
- Blend placement no longer fills in the inside of a structure. Any room more than one block wide was being packed with the original terrain.
- Placing a structure no longer floods the server log with post-processing warnings. Those blocks are settled properly instead.
