package com.cyberlogitec.ap_service_gcp.util;

import java.io.IOException;
import java.security.GeneralSecurityException;

@FunctionalInterface
public interface ApiAction<T> {
    T execute() throws IOException;
}
