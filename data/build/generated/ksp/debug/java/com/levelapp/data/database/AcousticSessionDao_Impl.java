package com.levelapp.data.database;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AcousticSessionDao_Impl implements AcousticSessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SessionEntity> __insertionAdapterOfSessionEntity;

  private final EntityInsertionAdapter<MeasurementPointEntity> __insertionAdapterOfMeasurementPointEntity;

  public AcousticSessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSessionEntity = new EntityInsertionAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sessions` (`id`,`title`,`workerName`,`evaluatorName`,`profileId`,`status`,`startedAtEpochMs`,`endedAtEpochMs`,`laeq`,`lasMax`,`twa`,`dosePercent`,`notes`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getWorkerName());
        statement.bindString(4, entity.getEvaluatorName());
        statement.bindString(5, entity.getProfileId());
        statement.bindString(6, entity.getStatus());
        statement.bindLong(7, entity.getStartedAtEpochMs());
        if (entity.getEndedAtEpochMs() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getEndedAtEpochMs());
        }
        statement.bindDouble(9, entity.getLaeq());
        statement.bindDouble(10, entity.getLasMax());
        statement.bindDouble(11, entity.getTwa());
        statement.bindDouble(12, entity.getDosePercent());
        statement.bindString(13, entity.getNotes());
      }
    };
    this.__insertionAdapterOfMeasurementPointEntity = new EntityInsertionAdapter<MeasurementPointEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `measurement_points` (`id`,`session_id`,`timestampEpochMs`,`dbA`,`laeq`,`lasMax`,`twa`,`dosePercent`,`durationSeconds`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MeasurementPointEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getSessionId());
        statement.bindLong(3, entity.getTimestampEpochMs());
        statement.bindDouble(4, entity.getDbA());
        statement.bindDouble(5, entity.getLaeq());
        statement.bindDouble(6, entity.getLasMax());
        statement.bindDouble(7, entity.getTwa());
        statement.bindDouble(8, entity.getDosePercent());
        statement.bindDouble(9, entity.getDurationSeconds());
      }
    };
  }

  @Override
  public Object insertSession(final SessionEntity session,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSessionEntity.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertMeasurementPoint(final MeasurementPointEntity point,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMeasurementPointEntity.insertAndReturnId(point);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SessionEntity>> observeSessions() {
    final String _sql = "SELECT * FROM sessions ORDER BY startedAtEpochMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sessions"}, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfWorkerName = CursorUtil.getColumnIndexOrThrow(_cursor, "workerName");
          final int _cursorIndexOfEvaluatorName = CursorUtil.getColumnIndexOrThrow(_cursor, "evaluatorName");
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfStartedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAtEpochMs");
          final int _cursorIndexOfEndedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAtEpochMs");
          final int _cursorIndexOfLaeq = CursorUtil.getColumnIndexOrThrow(_cursor, "laeq");
          final int _cursorIndexOfLasMax = CursorUtil.getColumnIndexOrThrow(_cursor, "lasMax");
          final int _cursorIndexOfTwa = CursorUtil.getColumnIndexOrThrow(_cursor, "twa");
          final int _cursorIndexOfDosePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "dosePercent");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpWorkerName;
            _tmpWorkerName = _cursor.getString(_cursorIndexOfWorkerName);
            final String _tmpEvaluatorName;
            _tmpEvaluatorName = _cursor.getString(_cursorIndexOfEvaluatorName);
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpStartedAtEpochMs;
            _tmpStartedAtEpochMs = _cursor.getLong(_cursorIndexOfStartedAtEpochMs);
            final Long _tmpEndedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfEndedAtEpochMs)) {
              _tmpEndedAtEpochMs = null;
            } else {
              _tmpEndedAtEpochMs = _cursor.getLong(_cursorIndexOfEndedAtEpochMs);
            }
            final double _tmpLaeq;
            _tmpLaeq = _cursor.getDouble(_cursorIndexOfLaeq);
            final double _tmpLasMax;
            _tmpLasMax = _cursor.getDouble(_cursorIndexOfLasMax);
            final double _tmpTwa;
            _tmpTwa = _cursor.getDouble(_cursorIndexOfTwa);
            final double _tmpDosePercent;
            _tmpDosePercent = _cursor.getDouble(_cursorIndexOfDosePercent);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            _item = new SessionEntity(_tmpId,_tmpTitle,_tmpWorkerName,_tmpEvaluatorName,_tmpProfileId,_tmpStatus,_tmpStartedAtEpochMs,_tmpEndedAtEpochMs,_tmpLaeq,_tmpLasMax,_tmpTwa,_tmpDosePercent,_tmpNotes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<MeasurementPointEntity>> observeMeasurementPoints(final long sessionId) {
    final String _sql = "SELECT * FROM measurement_points WHERE session_id = ? ORDER BY timestampEpochMs ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"measurement_points"}, new Callable<List<MeasurementPointEntity>>() {
      @Override
      @NonNull
      public List<MeasurementPointEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "session_id");
          final int _cursorIndexOfTimestampEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampEpochMs");
          final int _cursorIndexOfDbA = CursorUtil.getColumnIndexOrThrow(_cursor, "dbA");
          final int _cursorIndexOfLaeq = CursorUtil.getColumnIndexOrThrow(_cursor, "laeq");
          final int _cursorIndexOfLasMax = CursorUtil.getColumnIndexOrThrow(_cursor, "lasMax");
          final int _cursorIndexOfTwa = CursorUtil.getColumnIndexOrThrow(_cursor, "twa");
          final int _cursorIndexOfDosePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "dosePercent");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final List<MeasurementPointEntity> _result = new ArrayList<MeasurementPointEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MeasurementPointEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final long _tmpTimestampEpochMs;
            _tmpTimestampEpochMs = _cursor.getLong(_cursorIndexOfTimestampEpochMs);
            final double _tmpDbA;
            _tmpDbA = _cursor.getDouble(_cursorIndexOfDbA);
            final double _tmpLaeq;
            _tmpLaeq = _cursor.getDouble(_cursorIndexOfLaeq);
            final double _tmpLasMax;
            _tmpLasMax = _cursor.getDouble(_cursorIndexOfLasMax);
            final double _tmpTwa;
            _tmpTwa = _cursor.getDouble(_cursorIndexOfTwa);
            final double _tmpDosePercent;
            _tmpDosePercent = _cursor.getDouble(_cursorIndexOfDosePercent);
            final double _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getDouble(_cursorIndexOfDurationSeconds);
            _item = new MeasurementPointEntity(_tmpId,_tmpSessionId,_tmpTimestampEpochMs,_tmpDbA,_tmpLaeq,_tmpLasMax,_tmpTwa,_tmpDosePercent,_tmpDurationSeconds);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
