package fr.synxio.player.data.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Migrations Room rejouées sur une vraie base SQLite (JDBC), sans appareil.
 *
 * La base de départ est créée à partir du schéma exporté par Room pour sa version
 * (`app/schemas/…/N.json`), remplie d'une ligne par table ; après migration, le schéma
 * doit être celui que Room attend pour la version d'arrivée, et chaque ligne être
 * intacte. C'est ce que Room vérifie au démarrage : un écart y ferait planter l'app.
 */
class MigrationsTest {

    private lateinit var connection: Connection

    @Before
    fun open() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun close() = connection.close()

    @Test
    fun `15 vers 16 - signets ajoutes, aucune donnee perdue`() {
        createFromSchema(15)
        val before = snapshot()
        migrate(MIGRATION_15_16)
        assertSchemaMatches(16)
        assertEquals(before, snapshot().filterKeys { it in before })
        assertEquals(0, rowCount("bookmarks"))
    }

    @Test
    fun `chaine complete depuis la version 2 - schema conforme a chaque etape, lignes conservees`() {
        createFromSchema(2)
        val seeded = snapshot()
        for (version in 2 until LATEST) {
            val migration = ALL_MIGRATIONS.single { it.startVersion == version }
            assertEquals(version + 1, migration.endVersion)
            migrate(migration)
            assertSchemaMatches(version + 1)
        }
        // Les tables de la version 2 ont toutes gardé leur ligne.
        seeded.keys.forEach { table -> assertEquals(table, 1, rowCount(table)) }
    }

    // --- Base créée depuis un schéma exporté ---------------------------------------------

    private fun schemaJson(version: Int): JsonObject {
        val file = File(SCHEMA_DIR, "$version.json").takeIf { it.exists() }
            ?: File("app/$SCHEMA_DIR", "$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun entities(version: Int) = schemaJson(version)["entities"]!!.jsonArray.map { it.jsonObject }

    private fun createFromSchema(version: Int) {
        connection.createStatement().use { st ->
            entities(version).forEach { entity ->
                val table = entity.string("tableName")
                st.execute(entity.string("createSql").replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    st.execute(index.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
                }
                seed(table, entity)
            }
        }
    }

    /** Une ligne par table, valeurs non nulles adaptées à chaque colonne. */
    private fun seed(table: String, entity: JsonObject) {
        val fields = entity["fields"]!!.jsonArray.map { it.jsonObject }
        val columns = fields.joinToString { "`${it.string("columnName")}`" }
        val values = fields.joinToString { field ->
            when (field.string("affinity")) {
                "INTEGER" -> "1"
                "REAL" -> "1.5"
                "BLOB" -> "x'00'"
                else -> "'${table}-${field.string("columnName")}'"
            }
        }
        connection.createStatement().use { it.execute("INSERT INTO `$table` ($columns) VALUES ($values)") }
    }

    private fun migrate(migration: androidx.room.migration.Migration) = migration.migrate(supportDatabase())

    // --- Comparaison des schémas ---------------------------------------------------------

    private data class Column(val name: String, val affinity: String, val notNull: Boolean)
    private data class Index(val name: String, val unique: Boolean, val columns: List<String>)
    private data class Table(val columns: Set<Column>, val primaryKey: List<String>, val indices: Set<Index>)

    private fun expected(version: Int): Map<String, Table> = entities(version).associate { entity ->
        val columns = entity["fields"]!!.jsonArray.map { it.jsonObject }.map {
            Column(it.string("columnName"), it.string("affinity").uppercase(), it.flag("notNull"))
        }.toSet()
        val pk = entity["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
        val indices = entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject }.map {
            Index(
                it.string("name"),
                it.flag("unique"),
                it["columnNames"]!!.jsonArray.map { c -> c.jsonPrimitive.content },
            )
        }.toSet()
        entity.string("tableName") to Table(columns, pk, indices)
    }

    private fun actual(): Map<String, Table> = tables().associateWith { table ->
        val info = rows("PRAGMA table_info(`$table`)")
        val columns = info.map {
            Column(it["name"]!!, it["type"]!!.uppercase(), it["notnull"] == "1")
        }.toSet()
        val pk = info.filter { it["pk"] != "0" }.sortedBy { it["pk"]!!.toInt() }.map { it["name"]!! }
        val indices = rows("PRAGMA index_list(`$table`)")
            .filter { it["origin"] == "c" }
            .map { index ->
                val name = index["name"]!!
                Index(name, index["unique"] == "1", rows("PRAGMA index_info(`$name`)").sortedBy { it["seqno"]!!.toInt() }.map { it["name"]!! })
            }.toSet()
        Table(columns, pk, indices)
    }

    private fun assertSchemaMatches(version: Int) {
        val expected = expected(version)
        val actual = actual()
        assertEquals("tables, version $version", expected.keys, actual.keys)
        expected.forEach { (table, shape) -> assertEquals("table $table, version $version", shape, actual[table]) }
    }

    // --- Lecture ------------------------------------------------------------------------

    private fun tables(): List<String> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table'")
            .map { it["name"]!! }
            .filter { !it.startsWith("sqlite_") && it != "room_master_table" && it != "android_metadata" }

    private fun snapshot(): Map<String, List<Map<String, String?>>> =
        tables().associateWith { rows("SELECT * FROM `$it`") }

    private fun rowCount(table: String): Int = rows("SELECT COUNT(*) AS n FROM `$table`").single()["n"]!!.toInt()

    private fun rows(sql: String): List<Map<String, String?>> = connection.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val meta = rs.metaData
            buildList {
                while (rs.next()) {
                    add((1..meta.columnCount).associate { meta.getColumnLabel(it) to rs.getString(it) })
                }
            }
        }
    }

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content

    /** Room n'écrit pas les drapeaux faux dans ses schémas. */
    private fun JsonObject.flag(key: String) = this[key]?.jsonPrimitive?.boolean ?: false

    // --- SupportSQLiteDatabase minimal au-dessus de JDBC --------------------------------

    /**
     * Les migrations n'appellent que `execSQL` et `query` (suivi d'un parcours de curseur) :
     * c'est tout ce que ce double implémente. Tout autre appel échoue bruyamment.
     */
    private fun supportDatabase(): SupportSQLiteDatabase = proxy { name, args ->
        when (name) {
            "execSQL" -> {
                val sql = args[0] as String
                val bind = args.getOrNull(1) as? Array<*>
                connection.prepareStatement(sql).use { st ->
                    bind?.forEachIndexed { i, v -> st.setObject(i + 1, v) }
                    st.execute()
                }
                Unit
            }
            "query" -> cursor(rows(args[0] as String))
            else -> throw UnsupportedOperationException("SupportSQLiteDatabase.$name")
        }
    }

    private fun cursor(rows: List<Map<String, String?>>): Cursor {
        val columns = rows.firstOrNull()?.keys?.toList().orEmpty()
        var position = -1
        return proxy { name, args ->
            when (name) {
                "moveToNext" -> (++position < rows.size)
                "getColumnIndex" -> columns.indexOf(args[0] as String)
                "getString" -> rows[position][columns[args[0] as Int]]
                "getCount" -> rows.size
                "close" -> Unit
                else -> throw UnsupportedOperationException("Cursor.$name")
            }
        }
    }

    private inline fun <reified T> proxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        } as T

    private companion object {
        const val SCHEMA_DIR = "schemas/fr.synxio.player.data.db.SynxioDatabase"
        const val LATEST = 16
    }
}
