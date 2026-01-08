package com.cyberlogitec.ap_service_gcp.util;

import java.io.IOException;

@FunctionalInterface
public interface ApiAction<T> {
    T execute() throws IOException;
}
