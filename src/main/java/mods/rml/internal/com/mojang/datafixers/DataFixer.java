// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package mods.rml.internal.com.mojang.datafixers;

import mods.rml.internal.com.mojang.datafixers.schemas.Schema;
import mods.rml.internal.com.mojang.serialization.Dynamic;

public interface DataFixer {
    <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion);

    Schema getSchema(int key);
}
