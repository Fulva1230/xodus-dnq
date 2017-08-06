package kotlinx.dnq.query

import com.google.common.truth.Truth
import kotlinx.dnq.DBTest
import kotlinx.dnq.XdEntity
import kotlinx.dnq.transactional
import org.junit.Before
import org.junit.Test


class MapDistinctTest : DBTest() {

    @Before
    fun initStructure() {
        val boss = store.transactional {
            User.new {
                login = "boss"
                skill = 1
            }
        }
        store.transactional {
            User.new {
                login = "fellow"
                supervisor = boss
                skill = 1
            }
        }

        val anotherGuy = store.transactional {
            User.new {
                login = "anotherFellow"
                skill = 1
            }
        }

        store.transactional {
            Contact.new {
                email = "boss@123.org"
                user = boss
            }
            Contact.new {
                email = "anotherFellow@123.org"
                user = anotherGuy
            }
        }
        store.transactional {

            val jb = Team.new {
                name = "jb"
            }
            Team.new {
                name = "epam"
            }

            Fellow.new {
                name = "fellow1"
                team = jb
            }

            Fellow.new {
                name = "fellow2"
                team = jb
            }
        }
    }

    @Test
    fun `mapDistinct should work with xdLink`() {
        store.transactional {
            User.all().mapDistinct(User::supervisor).assertThatSizeIsEqualTo(1)
            User.all().mapDistinct { it.supervisor }.assertThatSizeIsEqualTo(1)
        }
    }

    @Test
    fun `flatMapDistinct should work with xdLink`() {
        store.transactional {
            User.all().flatMapDistinct(User::contacts).assertThatSizeIsEqualTo(2)
            User.all().flatMapDistinct { it.contacts }.assertThatSizeIsEqualTo(2)
        }
    }

    @Test
    fun `mapDistinct should work with xdParent`() {
        store.transactional {
            Fellow.all().mapDistinct(Fellow::team).assertThatSizeIsEqualTo(1)
            Fellow.all().mapDistinct { it.team }.assertThatSizeIsEqualTo(1)
        }
    }

    @Test
    fun `flatMapDistinct should work with xdChildren`() {
        store.transactional {
            Team.all().flatMapDistinct(Team::fellows).assertThatSizeIsEqualTo(2)
            Team.all().flatMapDistinct { it.fellows }.assertThatSizeIsEqualTo(2)
        }
    }

    private fun <XD : XdEntity> XdQuery<XD>.assertThatSizeIsEqualTo(value: Int) {
        Truth.assertThat(this.size()).isEqualTo(value)
        Truth.assertThat(this.asSequence().count()).isEqualTo(value)
    }
}
