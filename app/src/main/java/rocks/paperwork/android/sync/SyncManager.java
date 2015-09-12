package rocks.paperwork.android.sync;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rocks.paperwork.android.adapters.NotesAdapter.Note;
import rocks.paperwork.android.data.DatabaseContract;
import rocks.paperwork.android.data.NoteDataSource;

/**
 * Syncs local and remote version of objects
 */
public class SyncManager
{
    private final String LOG_TAG = SyncManager.class.getSimpleName();
    private final Context mContext;

    public SyncManager(Context context)
    {
        mContext = context;
    }

    /**
     * Syncs local and remote notes. Upload new local notes first to the server before calling this method
     *
     * @param localNotes  Locally stored notes
     * @param remoteNotes Notes from the server
     */
    public NotesToSync syncNotes(List<Note> localNotes, List<Note> remoteNotes)
    {
        Map<String, Note> localNoteIds = new HashMap<>();
        Map<String, Note> remoteNoteIds = new HashMap<>();

        List<Note> updatedOnServer = new ArrayList<>();
        List<Note> noteUpdatesForServer = new ArrayList<>();
        List<Note> newRemoteNotes = new ArrayList<>();
        List<Note> localNotesToDelete = new ArrayList<>();
        List<Note> notesMovedLocally = new ArrayList<>();

        NoteDataSource dataSource = NoteDataSource.getInstance(mContext);

        for (Note localNote : localNotes)
        {
            localNoteIds.put(localNote.getId(), localNote);
        }

        for (Note remoteNote : remoteNotes)
        {
            remoteNoteIds.put(remoteNote.getId(), remoteNote);
        }

        for (String key : localNoteIds.keySet())
        {
            Note localNote = localNoteIds.get(key);

            // check whether local note is uploaded to the server and
            if (remoteNoteIds.containsKey(key))
            {
                // note is uploaded to the server, get notes which need to synced

                Note remoteNote = remoteNoteIds.get(key);

                if (localNote.getSyncStatus() == DatabaseContract.NoteEntry.NOTE_STATUS.edited)
                {
                    if (localNote.getUpdatedAt().after(remoteNote.getUpdatedAt()))
                    {
                        // local note is newer
                        noteUpdatesForServer.add(localNote);
                        remoteNoteIds.remove(key);
                    }
                }
                else if (localNote.getUpdatedAt().before(remoteNote.getUpdatedAt()))
                {
                    // remote note is newer
                    if (localNote.getSyncStatus() == DatabaseContract.NoteEntry.NOTE_STATUS.synced)
                    {
                        updatedOnServer.add(remoteNote);
                        remoteNoteIds.remove(key);
                    }
                    else if (localNote.getSyncStatus() == DatabaseContract.NoteEntry.NOTE_STATUS.edited)
                    {
                        // FIXME note was edited locally and on the server -> conflict
                        Log.d(LOG_TAG, "Sync conflict");
                        // overwrite local note
                        updatedOnServer.add(remoteNote);
                    }
                }
            }
            else
            {
                // note is not availableon server anmore and needs to e deleted
                localNotesToDelete.add(localNote);
            }
        }

        for (String key : remoteNoteIds.keySet())
        {
            Note remoteNote = remoteNoteIds.get(key);

            Log.e(LOG_TAG, remoteNote.getTitle());

            if (!localNoteIds.containsKey(key))
            {
                // note only exists on server
                newRemoteNotes.add(remoteNote);
            }
            else
            {
                Log.e(LOG_TAG, "Key should be already removed");
            }
        }

        return new NotesToSync(noteUpdatesForServer, updatedOnServer, newRemoteNotes, localNotesToDelete);
    }

    public class NotesToSync
    {
        public final List<Note> locallyUpdatedNotes;
        public final List<Note> remoteUpdatedNotes;
        public final List<Note> remoteNewNotes;
        public final List<Note> localNotesToDelete;

        public NotesToSync(List<Note> locallyUpdatedNotes,
                           List<Note> remoteUpdatedNotes,
                           List<Note> remoteNewNotes,
                           List<Note> localNotesToDelete)
        {
            this.locallyUpdatedNotes = locallyUpdatedNotes;
            this.remoteUpdatedNotes = remoteUpdatedNotes;
            this.remoteNewNotes = remoteNewNotes;
            this.localNotesToDelete = localNotesToDelete;
        }
    }
}