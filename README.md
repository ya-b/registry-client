# A Java Client for Docker Registry API v2

A simple Java client for Docker Registry API v2, built on Google's Jib library.
It is recommended to use [jib](https://github.com/GoogleContainerTools/jib/tree/master/jib-core) directly.

## Usage

### Authentication
```java
// Set authentication for private registries
RegistryClient.authBasic("localhost:5000", "admin", "123456");

```

### Image Operations
```java
// Push image from tar file
RegistryClient.push("C:\\tmp\\docker.tar", "localhost:5000/test:v3");

// Pull image to tar file
RegistryClient.pull("localhost:5000/test:v1", "C:\\tmp\\docker2.tar");

// Copy image between repositories
RegistryClient.copy("localhost:5000/test:v1", "localhost:5000/test2:v1");

// Get image digest
RegistryClient.digest("localhost:5000/test:v1");

// Get image tags
List<String> tags = RegistryClient.tags("localhost:5000/test");

// Delete image (limited support)
RegistryClient.delete("localhost:5000/test@sha256:...");

// List repositories
RegistryClient.catalog("localhost:5000", 100, null);
```


### Maven
```xml
<dependency>
  <groupId>io.github.ya-b</groupId>
  <artifactId>registry-client</artifactId>
  <version>0.1.5</version>
</dependency>
```
