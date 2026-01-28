package com.cyberlogitec.ap.service.gcp.util;

import java.io.IOException;

@FunctionalInterface
public interface ApiAction<T> {
    T execute() throws IOException;
}
