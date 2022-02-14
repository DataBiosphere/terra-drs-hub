package bio.terra.drshub.config;

import org.immutables.value.Value;

@Value.Style(
    get = {"is*", "get*"},
    typeImmutable = "*",
    typeAbstract = "*Interface",
    typeModifiable = "*")
public @interface PropertiesInterfaceStyle {}
