package kotlinx.dnq

import com.google.common.truth.Truth.assertThat
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.util.getDBName
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith


class DBNameTest {

    open class Parent(override val entity: Entity) : XdEnumEntity(entity) {
        companion object : XdNaturalEntityType<Parent>()

        open val overriddenPropWithDbName by xdStringProp(dbName = "overriddenProp")
        open val overriddenPropWithoutDbName by xdStringProp(dbName = "overriddenInChild")
        val parentPropWithDbName by xdStringProp(dbName = "parentProp")
    }

    class Child(entity: Entity) : Parent(entity) {
        companion object : XdNaturalEntityType<Child>()

        override val overriddenPropWithDbName by xdStringProp(dbName = "overriddenChildProp")
        override val overriddenPropWithoutDbName by xdStringProp()

        val propWithDbName by xdStringProp(dbName = "dbProperty")
        val propWithoutDbName by xdStringProp()
    }

    class NoXdEntityTypeEntity(override val entity: Entity) : XdEntity() {
        companion object

        val prop by xdStringProp()
    }

    @Before
    fun before() {
        XdModel.registerNode(Child)
    }

    @Test
    fun `getDBName should return dbName if it exists`() {
        assertThat(Child::propWithDbName.getDBName())
                .isEqualTo("dbProperty")
        assertThat(Child::propWithoutDbName.getDBName())
                .isEqualTo(Child::propWithoutDbName.name)
    }

    @Test
    fun `getDBName should return dbName for parent's property`() {
        assertThat(Child::parentPropWithDbName.getDBName())
                .isEqualTo("parentProp")
        assertThat(Child::name.getDBName())
                .isEqualTo(XdEnumEntity.ENUM_CONST_NAME_FIELD)
    }

    @Test
    fun `getDBName should take more priority to children properties`() {
        assertThat(Child::overriddenPropWithDbName.getDBName())
                .isEqualTo("overriddenChildProp")
        assertThat(Child::overriddenPropWithoutDbName.getDBName())
                .isEqualTo(Child::overriddenPropWithoutDbName.name)
    }

    @Test()
    fun `getDBName should throw on properties of an XdEntity class without XdEntityType companion object`() {
        assertFailsWith<IllegalArgumentException> {
            NoXdEntityTypeEntity::prop.getDBName()
        }
    }
}