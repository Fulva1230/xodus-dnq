/**
 * Copyright 2006 - 2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kotlinx.dnq.link

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.query.metadata.AssociationEndCardinality
import jetbrains.exodus.query.metadata.AssociationEndType
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdEntityType
import kotlinx.dnq.query.XdMutableQuery
import kotlinx.dnq.query.isNotEmpty
import kotlinx.dnq.util.reattach
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

open class XdOneToManyLink<R : XdEntity, T : XdEntity>(
        oppositeEntityType: XdEntityType<T>,
        override val oppositeField: KProperty1<T, R?>,
        dbPropertyName: String?,
        dbOppositePropertyName: String?,
        onDeletePolicy: OnDeletePolicy,
        onTargetDeletePolicy: OnDeletePolicy,
        required: Boolean
) : VectorLink<R, T>, XdLink<R, T>(
        oppositeEntityType,
        dbPropertyName,
        dbOppositePropertyName,
        if (required) AssociationEndCardinality._1_n else AssociationEndCardinality._0_n,
        AssociationEndType.UndirectedAssociationEnd,
        onDeletePolicy,
        onTargetDeletePolicy
) {

    override fun getValue(thisRef: R, property: KProperty<*>): XdMutableQuery<T> {
        return object : XdMutableQuery<T>(oppositeEntityType) {
            override val entityIterable: Iterable<Entity>
                get() = thisRef.reattach().getLinks(property.dbName)

            override fun add(entity: T) {
                entity.reattach().setManyToOne(dbOppositePropertyName
                        ?: oppositeField.name, property.dbName, thisRef.reattach())
            }

            override fun remove(entity: T) {
                thisRef.reattach().removeOneToMany(dbOppositePropertyName
                        ?: oppositeField.name, property.dbName, entity.reattach())
            }

            override fun clear() {
                thisRef.reattach().clearOneToMany(dbOppositePropertyName ?: oppositeField.name, property.dbName)
            }

        }
    }

    override fun isDefined(thisRef: R, property: KProperty<*>): Boolean {
        return getValue(thisRef, property).isNotEmpty
    }
}