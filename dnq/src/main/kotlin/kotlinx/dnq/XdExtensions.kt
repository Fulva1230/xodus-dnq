/**
 * Copyright 2006 - 2018 JetBrains s.r.o.
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
package kotlinx.dnq

import jetbrains.exodus.database.TransientEntityStore
import jetbrains.exodus.database.TransientStoreSession
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdModel.toXd

val Entity.wrapper: XdEntity get() = toXd(this)

@Deprecated("Use toXd() instead. May be removed after 01.09.2017", ReplaceWith("toXd<T>()"))
fun <T : XdEntity> Entity.wrapper() = XdModel.toXd<T>(this)

fun <T : XdEntity> Entity.toXd() = XdModel.toXd<T>(this)

val TransientEntityStore.session: TransientStoreSession get() = threadSession ?: throw IllegalStateException("No current transient session.")