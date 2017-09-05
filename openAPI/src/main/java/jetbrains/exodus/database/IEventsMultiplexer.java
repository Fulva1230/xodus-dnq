/**
 * Copyright 2006 - 2017 JetBrains s.r.o.
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
package jetbrains.exodus.database;

import jetbrains.exodus.entitystore.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface IEventsMultiplexer {
    void flushed(TransientChangesTracker oldChangesTracker, Set<TransientEntityChange> changesDescription);

    void onClose(TransientEntityStore transientEntityStore);

    void addListener(@NotNull Entity e, @NotNull IEntityListener listener);

    void removeListener(@NotNull Entity e, @NotNull IEntityListener listener);

    void addListener(@NotNull String entityType, @NotNull IEntityListener listener);

    void removeListener(@NotNull String entityType, @NotNull IEntityListener listener);
}
