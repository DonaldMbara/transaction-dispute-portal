Future authentication services, guards and interceptors belong here. Foundation reserves this boundary without executable authentication or token state.

Future HttpClient requests use relative `/api/v1` URLs. The Angular development server forwards `/api/**` to localhost:8080 unchanged; future deployment uses a same-origin reverse proxy. No absolute browser API URL, base service, registered no-op interceptor or CORS configuration is needed in foundation. Actuator is checked directly on the backend and is not proxied.
