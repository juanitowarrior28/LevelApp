package com.levelapp.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AcousticDatabase_Impl extends AcousticDatabase {
  private volatile AcousticSessionDao _acousticSessionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `workerName` TEXT NOT NULL, `evaluatorName` TEXT NOT NULL, `profileId` TEXT NOT NULL, `status` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `endedAtEpochMs` INTEGER, `laeq` REAL NOT NULL, `lasMax` REAL NOT NULL, `twa` REAL NOT NULL, `dosePercent` REAL NOT NULL, `notes` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `measurement_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` INTEGER NOT NULL, `timestampEpochMs` INTEGER NOT NULL, `dbA` REAL NOT NULL, `laeq` REAL NOT NULL, `lasMax` REAL NOT NULL, `twa` REAL NOT NULL, `dosePercent` REAL NOT NULL, `durationSeconds` REAL NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bfa1ef0cbc8d7fbf79900cdfeb1aee38')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sessions`");
        db.execSQL("DROP TABLE IF EXISTS `measurement_points`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSessions = new HashMap<String, TableInfo.Column>(13);
        _columnsSessions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("workerName", new TableInfo.Column("workerName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("evaluatorName", new TableInfo.Column("evaluatorName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("profileId", new TableInfo.Column("profileId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("startedAtEpochMs", new TableInfo.Column("startedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("endedAtEpochMs", new TableInfo.Column("endedAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("laeq", new TableInfo.Column("laeq", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("lasMax", new TableInfo.Column("lasMax", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("twa", new TableInfo.Column("twa", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("dosePercent", new TableInfo.Column("dosePercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSessions = new TableInfo("sessions", _columnsSessions, _foreignKeysSessions, _indicesSessions);
        final TableInfo _existingSessions = TableInfo.read(db, "sessions");
        if (!_infoSessions.equals(_existingSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "sessions(com.levelapp.data.database.SessionEntity).\n"
                  + " Expected:\n" + _infoSessions + "\n"
                  + " Found:\n" + _existingSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsMeasurementPoints = new HashMap<String, TableInfo.Column>(9);
        _columnsMeasurementPoints.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("session_id", new TableInfo.Column("session_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("timestampEpochMs", new TableInfo.Column("timestampEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("dbA", new TableInfo.Column("dbA", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("laeq", new TableInfo.Column("laeq", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("lasMax", new TableInfo.Column("lasMax", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("twa", new TableInfo.Column("twa", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("dosePercent", new TableInfo.Column("dosePercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMeasurementPoints.put("durationSeconds", new TableInfo.Column("durationSeconds", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMeasurementPoints = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMeasurementPoints = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMeasurementPoints = new TableInfo("measurement_points", _columnsMeasurementPoints, _foreignKeysMeasurementPoints, _indicesMeasurementPoints);
        final TableInfo _existingMeasurementPoints = TableInfo.read(db, "measurement_points");
        if (!_infoMeasurementPoints.equals(_existingMeasurementPoints)) {
          return new RoomOpenHelper.ValidationResult(false, "measurement_points(com.levelapp.data.database.MeasurementPointEntity).\n"
                  + " Expected:\n" + _infoMeasurementPoints + "\n"
                  + " Found:\n" + _existingMeasurementPoints);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "bfa1ef0cbc8d7fbf79900cdfeb1aee38", "e81cbe20f8476884f9f3116bfd0af6b7");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sessions","measurement_points");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `sessions`");
      _db.execSQL("DELETE FROM `measurement_points`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(AcousticSessionDao.class, AcousticSessionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public AcousticSessionDao acousticSessionDao() {
    if (_acousticSessionDao != null) {
      return _acousticSessionDao;
    } else {
      synchronized(this) {
        if(_acousticSessionDao == null) {
          _acousticSessionDao = new AcousticSessionDao_Impl(this);
        }
        return _acousticSessionDao;
      }
    }
  }
}
