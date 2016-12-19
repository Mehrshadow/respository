package io.realm;


import android.annotation.TargetApi;
import android.os.Build;
import android.util.JsonReader;
import android.util.JsonToken;
import io.realm.RealmObjectSchema;
import io.realm.RealmSchema;
import io.realm.exceptions.RealmMigrationNeededException;
import io.realm.internal.ColumnInfo;
import io.realm.internal.LinkView;
import io.realm.internal.RealmObjectProxy;
import io.realm.internal.Row;
import io.realm.internal.SharedRealm;
import io.realm.internal.Table;
import io.realm.internal.TableOrView;
import io.realm.internal.android.JsonUtils;
import io.realm.log.RealmLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DatabaseMapRealmProxy extends classes.DatabaseMap
    implements RealmObjectProxy, DatabaseMapRealmProxyInterface {

    static final class DatabaseMapColumnInfo extends ColumnInfo
        implements Cloneable {

        public long IDIndex;
        public long C_NameIndex;
        public long C_ipIndex;

        DatabaseMapColumnInfo(String path, Table table) {
            final Map<String, Long> indicesMap = new HashMap<String, Long>(3);
            this.IDIndex = getValidColumnIndex(path, table, "DatabaseMap", "ID");
            indicesMap.put("ID", this.IDIndex);
            this.C_NameIndex = getValidColumnIndex(path, table, "DatabaseMap", "C_Name");
            indicesMap.put("C_Name", this.C_NameIndex);
            this.C_ipIndex = getValidColumnIndex(path, table, "DatabaseMap", "C_ip");
            indicesMap.put("C_ip", this.C_ipIndex);

            setIndicesMap(indicesMap);
        }

        @Override
        public final void copyColumnInfoFrom(ColumnInfo other) {
            final DatabaseMapColumnInfo otherInfo = (DatabaseMapColumnInfo) other;
            this.IDIndex = otherInfo.IDIndex;
            this.C_NameIndex = otherInfo.C_NameIndex;
            this.C_ipIndex = otherInfo.C_ipIndex;

            setIndicesMap(otherInfo.getIndicesMap());
        }

        @Override
        public final DatabaseMapColumnInfo clone() {
            return (DatabaseMapColumnInfo) super.clone();
        }

    }
    private DatabaseMapColumnInfo columnInfo;
    private ProxyState proxyState;
    private static final List<String> FIELD_NAMES;
    static {
        List<String> fieldNames = new ArrayList<String>();
        fieldNames.add("ID");
        fieldNames.add("C_Name");
        fieldNames.add("C_ip");
        FIELD_NAMES = Collections.unmodifiableList(fieldNames);
    }

    DatabaseMapRealmProxy() {
        if (proxyState == null) {
            injectObjectContext();
        }
        proxyState.setConstructionFinished();
    }

    private void injectObjectContext() {
        final BaseRealm.RealmObjectContext context = BaseRealm.objectContext.get();
        this.columnInfo = (DatabaseMapColumnInfo) context.getColumnInfo();
        this.proxyState = new ProxyState(classes.DatabaseMap.class, this);
        proxyState.setRealm$realm(context.getRealm());
        proxyState.setRow$realm(context.getRow());
        proxyState.setAcceptDefaultValue$realm(context.getAcceptDefaultValue());
        proxyState.setExcludeFields$realm(context.getExcludeFields());
    }

    @SuppressWarnings("cast")
    public int realmGet$ID() {
        if (proxyState == null) {
            // Called from model's constructor. Inject context.
            injectObjectContext();
        }

        proxyState.getRealm$realm().checkIfValid();
        return (int) proxyState.getRow$realm().getLong(columnInfo.IDIndex);
    }

    public void realmSet$ID(int value) {
        if (proxyState == null) {
            // Called from model's constructor. Inject context.
            injectObjectContext();
        }

        if (proxyState.isUnderConstruction()) {
            if (!proxyState.getAcceptDefaultValue$realm()) {
                return;
            }
            final Row row = proxyState.getRow$realm();
            row.getTable().setLong(columnInfo.IDIndex, row.getIndex(), value, true);
            return;
        }

        proxyState.getRealm$realm().checkIfValid();
        proxyState.getRow$realm().setLong(columnInfo.IDIndex, value);
    }

    @SuppressWarnings("cast")
    public String realmGet$C_Name() {
        if (proxyState == null) {
            // Called from model's constructor. Inject context.
            injectObjectContext();
        }

        proxyState.getRealm$realm().checkIfValid();
        return (java.lang.String) proxyState.getRow$realm().getString(columnInfo.C_NameIndex);
    }

    public void realmSet$C_Name(String value) {
        if (proxyState == null) {
            // Called from model's constructor. Inject context.
            injectObjectContext();
        }

        if (proxyState.isUnderConstruction()) {
            if (!proxyState.getAcceptDefaultValue$realm()) {
                return;
            }
            final Row row = proxyState.getRow$realm();
            if (value == null) {
                row.getTable().setNull(columnInfo.C_NameIndex, row.getIndex(), true);
                return;
            }
            row.getTable().setString(columnInfo.C_NameIndex, row.getIndex(), value, true);
            return;
        }

        proxyState.getRealm$realm().checkIfValid();
        if (value == null) {
            proxyState.getRow$realm().setNull(columnInfo.C_NameIndex);
            return;
        }
        proxyState.getRow$realm().setString(columnInfo.C_NameIndex, value);
    }

    @SuppressWarnings("cast")
    public String realmGet$C_ip() {
        if (proxyState == null) {
            // Called from model's constructor. Inject context.
            injectObjectContext();
        }

        proxyState.getRealm$realm().checkIfValid();
        return (java.lang.String) proxyState.getRow$realm().getString(columnInfo.C_ipIndex);
    }

    public void realmSet$C_ip(String value) {
        if (proxyState == null) {
            // Called from model's constructor. Inject context.
            injectObjectContext();
        }

        if (proxyState.isUnderConstruction()) {
            if (!proxyState.getAcceptDefaultValue$realm()) {
                return;
            }
            final Row row = proxyState.getRow$realm();
            if (value == null) {
                row.getTable().setNull(columnInfo.C_ipIndex, row.getIndex(), true);
                return;
            }
            row.getTable().setString(columnInfo.C_ipIndex, row.getIndex(), value, true);
            return;
        }

        proxyState.getRealm$realm().checkIfValid();
        if (value == null) {
            proxyState.getRow$realm().setNull(columnInfo.C_ipIndex);
            return;
        }
        proxyState.getRow$realm().setString(columnInfo.C_ipIndex, value);
    }

    public static RealmObjectSchema createRealmObjectSchema(RealmSchema realmSchema) {
        if (!realmSchema.contains("DatabaseMap")) {
            RealmObjectSchema realmObjectSchema = realmSchema.create("DatabaseMap");
            realmObjectSchema.add(new Property("ID", RealmFieldType.INTEGER, !Property.PRIMARY_KEY, !Property.INDEXED, Property.REQUIRED));
            realmObjectSchema.add(new Property("C_Name", RealmFieldType.STRING, !Property.PRIMARY_KEY, !Property.INDEXED, !Property.REQUIRED));
            realmObjectSchema.add(new Property("C_ip", RealmFieldType.STRING, !Property.PRIMARY_KEY, !Property.INDEXED, !Property.REQUIRED));
            return realmObjectSchema;
        }
        return realmSchema.get("DatabaseMap");
    }

    public static Table initTable(SharedRealm sharedRealm) {
        if (!sharedRealm.hasTable("class_DatabaseMap")) {
            Table table = sharedRealm.getTable("class_DatabaseMap");
            table.addColumn(RealmFieldType.INTEGER, "ID", Table.NOT_NULLABLE);
            table.addColumn(RealmFieldType.STRING, "C_Name", Table.NULLABLE);
            table.addColumn(RealmFieldType.STRING, "C_ip", Table.NULLABLE);
            table.setPrimaryKey("");
            return table;
        }
        return sharedRealm.getTable("class_DatabaseMap");
    }

    public static DatabaseMapColumnInfo validateTable(SharedRealm sharedRealm, boolean allowExtraColumns) {
        if (sharedRealm.hasTable("class_DatabaseMap")) {
            Table table = sharedRealm.getTable("class_DatabaseMap");
            final long columnCount = table.getColumnCount();
            if (columnCount != 3) {
                if (columnCount < 3) {
                    throw new RealmMigrationNeededException(sharedRealm.getPath(), "Field count is less than expected - expected 3 but was " + columnCount);
                }
                if (allowExtraColumns) {
                    RealmLog.debug("Field count is more than expected - expected 3 but was %1$d", columnCount);
                } else {
                    throw new RealmMigrationNeededException(sharedRealm.getPath(), "Field count is more than expected - expected 3 but was " + columnCount);
                }
            }
            Map<String, RealmFieldType> columnTypes = new HashMap<String, RealmFieldType>();
            for (long i = 0; i < columnCount; i++) {
                columnTypes.put(table.getColumnName(i), table.getColumnType(i));
            }

            final DatabaseMapColumnInfo columnInfo = new DatabaseMapColumnInfo(sharedRealm.getPath(), table);

            if (!columnTypes.containsKey("ID")) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Missing field 'ID' in existing Realm file. Either remove field or migrate using io.realm.internal.Table.addColumn().");
            }
            if (columnTypes.get("ID") != RealmFieldType.INTEGER) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Invalid type 'int' for field 'ID' in existing Realm file.");
            }
            if (table.isColumnNullable(columnInfo.IDIndex)) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Field 'ID' does support null values in the existing Realm file. Use corresponding boxed type for field 'ID' or migrate using RealmObjectSchema.setNullable().");
            }
            if (!columnTypes.containsKey("C_Name")) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Missing field 'C_Name' in existing Realm file. Either remove field or migrate using io.realm.internal.Table.addColumn().");
            }
            if (columnTypes.get("C_Name") != RealmFieldType.STRING) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Invalid type 'String' for field 'C_Name' in existing Realm file.");
            }
            if (!table.isColumnNullable(columnInfo.C_NameIndex)) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Field 'C_Name' is required. Either set @Required to field 'C_Name' or migrate using RealmObjectSchema.setNullable().");
            }
            if (!columnTypes.containsKey("C_ip")) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Missing field 'C_ip' in existing Realm file. Either remove field or migrate using io.realm.internal.Table.addColumn().");
            }
            if (columnTypes.get("C_ip") != RealmFieldType.STRING) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Invalid type 'String' for field 'C_ip' in existing Realm file.");
            }
            if (!table.isColumnNullable(columnInfo.C_ipIndex)) {
                throw new RealmMigrationNeededException(sharedRealm.getPath(), "Field 'C_ip' is required. Either set @Required to field 'C_ip' or migrate using RealmObjectSchema.setNullable().");
            }
            return columnInfo;
        } else {
            throw new RealmMigrationNeededException(sharedRealm.getPath(), "The 'DatabaseMap' class is missing from the schema for this Realm.");
        }
    }

    public static String getTableName() {
        return "class_DatabaseMap";
    }

    public static List<String> getFieldNames() {
        return FIELD_NAMES;
    }

    @SuppressWarnings("cast")
    public static classes.DatabaseMap createOrUpdateUsingJsonObject(Realm realm, JSONObject json, boolean update)
        throws JSONException {
        final List<String> excludeFields = Collections.<String> emptyList();
        classes.DatabaseMap obj = realm.createObjectInternal(classes.DatabaseMap.class, true, excludeFields);
        if (json.has("ID")) {
            if (json.isNull("ID")) {
                throw new IllegalArgumentException("Trying to set non-nullable field 'ID' to null.");
            } else {
                ((DatabaseMapRealmProxyInterface) obj).realmSet$ID((int) json.getInt("ID"));
            }
        }
        if (json.has("C_Name")) {
            if (json.isNull("C_Name")) {
                ((DatabaseMapRealmProxyInterface) obj).realmSet$C_Name(null);
            } else {
                ((DatabaseMapRealmProxyInterface) obj).realmSet$C_Name((String) json.getString("C_Name"));
            }
        }
        if (json.has("C_ip")) {
            if (json.isNull("C_ip")) {
                ((DatabaseMapRealmProxyInterface) obj).realmSet$C_ip(null);
            } else {
                ((DatabaseMapRealmProxyInterface) obj).realmSet$C_ip((String) json.getString("C_ip"));
            }
        }
        return obj;
    }

    @SuppressWarnings("cast")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static classes.DatabaseMap createUsingJsonStream(Realm realm, JsonReader reader)
        throws IOException {
        classes.DatabaseMap obj = new classes.DatabaseMap();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("ID")) {
                if (reader.peek() == JsonToken.NULL) {
                    reader.skipValue();
                    throw new IllegalArgumentException("Trying to set non-nullable field 'ID' to null.");
                } else {
                    ((DatabaseMapRealmProxyInterface) obj).realmSet$ID((int) reader.nextInt());
                }
            } else if (name.equals("C_Name")) {
                if (reader.peek() == JsonToken.NULL) {
                    reader.skipValue();
                    ((DatabaseMapRealmProxyInterface) obj).realmSet$C_Name(null);
                } else {
                    ((DatabaseMapRealmProxyInterface) obj).realmSet$C_Name((String) reader.nextString());
                }
            } else if (name.equals("C_ip")) {
                if (reader.peek() == JsonToken.NULL) {
                    reader.skipValue();
                    ((DatabaseMapRealmProxyInterface) obj).realmSet$C_ip(null);
                } else {
                    ((DatabaseMapRealmProxyInterface) obj).realmSet$C_ip((String) reader.nextString());
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        obj = realm.copyToRealm(obj);
        return obj;
    }

    public static classes.DatabaseMap copyOrUpdate(Realm realm, classes.DatabaseMap object, boolean update, Map<RealmModel,RealmObjectProxy> cache) {
        if (object instanceof RealmObjectProxy && ((RealmObjectProxy) object).realmGet$proxyState().getRealm$realm() != null && ((RealmObjectProxy) object).realmGet$proxyState().getRealm$realm().threadId != realm.threadId) {
            throw new IllegalArgumentException("Objects which belong to Realm instances in other threads cannot be copied into this Realm instance.");
        }
        if (object instanceof RealmObjectProxy && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm() != null && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm().getPath().equals(realm.getPath())) {
            return object;
        }
        final BaseRealm.RealmObjectContext objectContext = BaseRealm.objectContext.get();
        RealmObjectProxy cachedRealmObject = cache.get(object);
        if (cachedRealmObject != null) {
            return (classes.DatabaseMap) cachedRealmObject;
        } else {
            return copy(realm, object, update, cache);
        }
    }

    public static classes.DatabaseMap copy(Realm realm, classes.DatabaseMap newObject, boolean update, Map<RealmModel,RealmObjectProxy> cache) {
        RealmObjectProxy cachedRealmObject = cache.get(newObject);
        if (cachedRealmObject != null) {
            return (classes.DatabaseMap) cachedRealmObject;
        } else {
            // rejecting default values to avoid creating unexpected objects from RealmModel/RealmList fields.
            classes.DatabaseMap realmObject = realm.createObjectInternal(classes.DatabaseMap.class, false, Collections.<String>emptyList());
            cache.put(newObject, (RealmObjectProxy) realmObject);
            ((DatabaseMapRealmProxyInterface) realmObject).realmSet$ID(((DatabaseMapRealmProxyInterface) newObject).realmGet$ID());
            ((DatabaseMapRealmProxyInterface) realmObject).realmSet$C_Name(((DatabaseMapRealmProxyInterface) newObject).realmGet$C_Name());
            ((DatabaseMapRealmProxyInterface) realmObject).realmSet$C_ip(((DatabaseMapRealmProxyInterface) newObject).realmGet$C_ip());
            return realmObject;
        }
    }

    public static long insert(Realm realm, classes.DatabaseMap object, Map<RealmModel,Long> cache) {
        if (object instanceof RealmObjectProxy && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm() != null && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm().getPath().equals(realm.getPath())) {
            return ((RealmObjectProxy)object).realmGet$proxyState().getRow$realm().getIndex();
        }
        Table table = realm.getTable(classes.DatabaseMap.class);
        long tableNativePtr = table.getNativeTablePointer();
        DatabaseMapColumnInfo columnInfo = (DatabaseMapColumnInfo) realm.schema.getColumnInfo(classes.DatabaseMap.class);
        long rowIndex = Table.nativeAddEmptyRow(tableNativePtr, 1);
        cache.put(object, rowIndex);
        Table.nativeSetLong(tableNativePtr, columnInfo.IDIndex, rowIndex, ((DatabaseMapRealmProxyInterface)object).realmGet$ID(), false);
        String realmGet$C_Name = ((DatabaseMapRealmProxyInterface)object).realmGet$C_Name();
        if (realmGet$C_Name != null) {
            Table.nativeSetString(tableNativePtr, columnInfo.C_NameIndex, rowIndex, realmGet$C_Name, false);
        }
        String realmGet$C_ip = ((DatabaseMapRealmProxyInterface)object).realmGet$C_ip();
        if (realmGet$C_ip != null) {
            Table.nativeSetString(tableNativePtr, columnInfo.C_ipIndex, rowIndex, realmGet$C_ip, false);
        }
        return rowIndex;
    }

    public static void insert(Realm realm, Iterator<? extends RealmModel> objects, Map<RealmModel,Long> cache) {
        Table table = realm.getTable(classes.DatabaseMap.class);
        long tableNativePtr = table.getNativeTablePointer();
        DatabaseMapColumnInfo columnInfo = (DatabaseMapColumnInfo) realm.schema.getColumnInfo(classes.DatabaseMap.class);
        classes.DatabaseMap object = null;
        while (objects.hasNext()) {
            object = (classes.DatabaseMap) objects.next();
            if(!cache.containsKey(object)) {
                if (object instanceof RealmObjectProxy && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm() != null && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm().getPath().equals(realm.getPath())) {
                    cache.put(object, ((RealmObjectProxy)object).realmGet$proxyState().getRow$realm().getIndex());
                    continue;
                }
                long rowIndex = Table.nativeAddEmptyRow(tableNativePtr, 1);
                cache.put(object, rowIndex);
                Table.nativeSetLong(tableNativePtr, columnInfo.IDIndex, rowIndex, ((DatabaseMapRealmProxyInterface)object).realmGet$ID(), false);
                String realmGet$C_Name = ((DatabaseMapRealmProxyInterface)object).realmGet$C_Name();
                if (realmGet$C_Name != null) {
                    Table.nativeSetString(tableNativePtr, columnInfo.C_NameIndex, rowIndex, realmGet$C_Name, false);
                }
                String realmGet$C_ip = ((DatabaseMapRealmProxyInterface)object).realmGet$C_ip();
                if (realmGet$C_ip != null) {
                    Table.nativeSetString(tableNativePtr, columnInfo.C_ipIndex, rowIndex, realmGet$C_ip, false);
                }
            }
        }
    }

    public static long insertOrUpdate(Realm realm, classes.DatabaseMap object, Map<RealmModel,Long> cache) {
        if (object instanceof RealmObjectProxy && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm() != null && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm().getPath().equals(realm.getPath())) {
            return ((RealmObjectProxy)object).realmGet$proxyState().getRow$realm().getIndex();
        }
        Table table = realm.getTable(classes.DatabaseMap.class);
        long tableNativePtr = table.getNativeTablePointer();
        DatabaseMapColumnInfo columnInfo = (DatabaseMapColumnInfo) realm.schema.getColumnInfo(classes.DatabaseMap.class);
        long rowIndex = Table.nativeAddEmptyRow(tableNativePtr, 1);
        cache.put(object, rowIndex);
        Table.nativeSetLong(tableNativePtr, columnInfo.IDIndex, rowIndex, ((DatabaseMapRealmProxyInterface)object).realmGet$ID(), false);
        String realmGet$C_Name = ((DatabaseMapRealmProxyInterface)object).realmGet$C_Name();
        if (realmGet$C_Name != null) {
            Table.nativeSetString(tableNativePtr, columnInfo.C_NameIndex, rowIndex, realmGet$C_Name, false);
        } else {
            Table.nativeSetNull(tableNativePtr, columnInfo.C_NameIndex, rowIndex, false);
        }
        String realmGet$C_ip = ((DatabaseMapRealmProxyInterface)object).realmGet$C_ip();
        if (realmGet$C_ip != null) {
            Table.nativeSetString(tableNativePtr, columnInfo.C_ipIndex, rowIndex, realmGet$C_ip, false);
        } else {
            Table.nativeSetNull(tableNativePtr, columnInfo.C_ipIndex, rowIndex, false);
        }
        return rowIndex;
    }

    public static void insertOrUpdate(Realm realm, Iterator<? extends RealmModel> objects, Map<RealmModel,Long> cache) {
        Table table = realm.getTable(classes.DatabaseMap.class);
        long tableNativePtr = table.getNativeTablePointer();
        DatabaseMapColumnInfo columnInfo = (DatabaseMapColumnInfo) realm.schema.getColumnInfo(classes.DatabaseMap.class);
        classes.DatabaseMap object = null;
        while (objects.hasNext()) {
            object = (classes.DatabaseMap) objects.next();
            if(!cache.containsKey(object)) {
                if (object instanceof RealmObjectProxy && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm() != null && ((RealmObjectProxy)object).realmGet$proxyState().getRealm$realm().getPath().equals(realm.getPath())) {
                    cache.put(object, ((RealmObjectProxy)object).realmGet$proxyState().getRow$realm().getIndex());
                    continue;
                }
                long rowIndex = Table.nativeAddEmptyRow(tableNativePtr, 1);
                cache.put(object, rowIndex);
                Table.nativeSetLong(tableNativePtr, columnInfo.IDIndex, rowIndex, ((DatabaseMapRealmProxyInterface)object).realmGet$ID(), false);
                String realmGet$C_Name = ((DatabaseMapRealmProxyInterface)object).realmGet$C_Name();
                if (realmGet$C_Name != null) {
                    Table.nativeSetString(tableNativePtr, columnInfo.C_NameIndex, rowIndex, realmGet$C_Name, false);
                } else {
                    Table.nativeSetNull(tableNativePtr, columnInfo.C_NameIndex, rowIndex, false);
                }
                String realmGet$C_ip = ((DatabaseMapRealmProxyInterface)object).realmGet$C_ip();
                if (realmGet$C_ip != null) {
                    Table.nativeSetString(tableNativePtr, columnInfo.C_ipIndex, rowIndex, realmGet$C_ip, false);
                } else {
                    Table.nativeSetNull(tableNativePtr, columnInfo.C_ipIndex, rowIndex, false);
                }
            }
        }
    }

    public static classes.DatabaseMap createDetachedCopy(classes.DatabaseMap realmObject, int currentDepth, int maxDepth, Map<RealmModel, CacheData<RealmModel>> cache) {
        if (currentDepth > maxDepth || realmObject == null) {
            return null;
        }
        CacheData<RealmModel> cachedObject = cache.get(realmObject);
        classes.DatabaseMap unmanagedObject;
        if (cachedObject != null) {
            // Reuse cached object or recreate it because it was encountered at a lower depth.
            if (currentDepth >= cachedObject.minDepth) {
                return (classes.DatabaseMap)cachedObject.object;
            } else {
                unmanagedObject = (classes.DatabaseMap)cachedObject.object;
                cachedObject.minDepth = currentDepth;
            }
        } else {
            unmanagedObject = new classes.DatabaseMap();
            cache.put(realmObject, new RealmObjectProxy.CacheData(currentDepth, unmanagedObject));
        }
        ((DatabaseMapRealmProxyInterface) unmanagedObject).realmSet$ID(((DatabaseMapRealmProxyInterface) realmObject).realmGet$ID());
        ((DatabaseMapRealmProxyInterface) unmanagedObject).realmSet$C_Name(((DatabaseMapRealmProxyInterface) realmObject).realmGet$C_Name());
        ((DatabaseMapRealmProxyInterface) unmanagedObject).realmSet$C_ip(((DatabaseMapRealmProxyInterface) realmObject).realmGet$C_ip());
        return unmanagedObject;
    }

    @Override
    public String toString() {
        if (!RealmObject.isValid(this)) {
            return "Invalid object";
        }
        StringBuilder stringBuilder = new StringBuilder("DatabaseMap = [");
        stringBuilder.append("{ID:");
        stringBuilder.append(realmGet$ID());
        stringBuilder.append("}");
        stringBuilder.append(",");
        stringBuilder.append("{C_Name:");
        stringBuilder.append(realmGet$C_Name() != null ? realmGet$C_Name() : "null");
        stringBuilder.append("}");
        stringBuilder.append(",");
        stringBuilder.append("{C_ip:");
        stringBuilder.append(realmGet$C_ip() != null ? realmGet$C_ip() : "null");
        stringBuilder.append("}");
        stringBuilder.append("]");
        return stringBuilder.toString();
    }

    @Override
    public ProxyState realmGet$proxyState() {
        return proxyState;
    }

    @Override
    public int hashCode() {
        String realmName = proxyState.getRealm$realm().getPath();
        String tableName = proxyState.getRow$realm().getTable().getName();
        long rowIndex = proxyState.getRow$realm().getIndex();

        int result = 17;
        result = 31 * result + ((realmName != null) ? realmName.hashCode() : 0);
        result = 31 * result + ((tableName != null) ? tableName.hashCode() : 0);
        result = 31 * result + (int) (rowIndex ^ (rowIndex >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseMapRealmProxy aDatabaseMap = (DatabaseMapRealmProxy)o;

        String path = proxyState.getRealm$realm().getPath();
        String otherPath = aDatabaseMap.proxyState.getRealm$realm().getPath();
        if (path != null ? !path.equals(otherPath) : otherPath != null) return false;

        String tableName = proxyState.getRow$realm().getTable().getName();
        String otherTableName = aDatabaseMap.proxyState.getRow$realm().getTable().getName();
        if (tableName != null ? !tableName.equals(otherTableName) : otherTableName != null) return false;

        if (proxyState.getRow$realm().getIndex() != aDatabaseMap.proxyState.getRow$realm().getIndex()) return false;

        return true;
    }

}
