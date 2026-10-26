# A sync manager's cursors are persisted under `LBSyncManager.syncKey`, which defaults to the class simple name.
# Without this rule R8 renames managers between builds and merges look-alike managers into one class, so several
# managers read and write a single cursor and skip each other's records.
-keepnames class * extends studio.lunabee.synchronization.syncmanager.LBSyncManager
