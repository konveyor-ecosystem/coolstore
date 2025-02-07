package com.redhat.coolstore;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class CoolStoreApplication {
    public static void main(String... args) {
        Quarkus.run(args);
    }
} 