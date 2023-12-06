/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.file;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.LinksStrategy;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A factory for creating {@link AttributeBasedFileVisitDetails} instances.
 * Fallbacks to UnauthorizedFileVisitDetails in cases when attributes are not available.
 * Fallbacks to DefaultFileVisitDetails in cases when attributes are not reliable.
 */
public class AttributeBasedFileVisitDetailsFactory {

    /**
     * Returns FileVisitDetails for the given relativePath.
     *
     * @param path Path of the file
     * @param relativePath RelativePath of the file
     * @param attrs attributes of the path, null if not available
     * @param stopFlag transient flag to stop visiting
     * @param fileSystem for Chmod and Stat
     * @param linksStrategy links strategy
     * @return FileVisitDetails
     */
    public static FileVisitDetails getRootFileVisitDetails(
        Path path,
        RelativePath relativePath,
        @Nullable BasicFileAttributes attrs,
        AtomicBoolean stopFlag,
        FileSystem fileSystem,
        LinksStrategy linksStrategy
    ) {
        File file = path.toFile();
        if (attrs == null) {
            return new UnauthorizedFileVisitDetails(file, relativePath);
        } else {
            if (relativePath.getSegments().length == 0) {
                if (linksStrategy.preserveLinks() && Files.isSymbolicLink(path)) {
                    FileVisitDetails temp = createFileVisitDetails(file, relativePath, attrs, stopFlag, fileSystem, true);
                    linksStrategy.maybeThrowOnBrokenLink(temp);
                    relativePath = new RelativePath(true, file.getName());
                } else if (attrs.isRegularFile()) {
                    relativePath = new RelativePath(true, file.getName());
                }
            }
            return createFileVisitDetails(file, relativePath, attrs, stopFlag, fileSystem, linksStrategy.preserveLinks() || attrs.isSymbolicLink());
        }
    }

    /**
     * Gets attributes and returns FileVisitDetails for the given relativePath.
     *
     * @param path Path of the file
     * @param relativePath RelativePath of the file
     * @param stopFlag transient flag to stop visiting
     * @param fileSystem for Chmod and Stat
     * @param linksStrategy links strategy
     * @return FileVisitDetails
     */
    public static FileVisitDetails getRootFileVisitDetails(
        Path path,
        RelativePath relativePath,
        AtomicBoolean stopFlag,
        FileSystem fileSystem,
        LinksStrategy linksStrategy
    ) {
        BasicFileAttributes attrs = getAttributes(path);
        return getRootFileVisitDetails(path, relativePath, attrs, stopFlag, fileSystem, linksStrategy);
    }

    /**
     * Constructs proper RelativePath based on attributes and returns FileVisitDetails for the given relativePath.
     *
     * @param path Path of the file
     * @param parentPath RelativePath of the parent
     * @param attrs attributes of the path, null if not available
     * @param stopFlag transient flag to stop visiting
     * @param fileSystem for Chmod and Stat
     * @param linksStrategy links strategy
     * @return FileVisitDetails
     */
    public static FileVisitDetails getFileVisitDetails(
        Path path,
        RelativePath parentPath,
        @Nullable BasicFileAttributes attrs,
        AtomicBoolean stopFlag,
        FileSystem fileSystem,
        LinksStrategy linksStrategy
    ) {
        File file = path.toFile();
        if (attrs == null) {
            RelativePath relativePath = parentPath.append(false, file.getName());
            return new UnauthorizedFileVisitDetails(file, relativePath);
        } else {
            RelativePath relativePath = parentPath.append(!attrs.isDirectory() || (linksStrategy.preserveLinks() && Files.isSymbolicLink(path)), file.getName());
            return createFileVisitDetails(file, relativePath, attrs, stopFlag, fileSystem, linksStrategy.preserveLinks() || attrs.isSymbolicLink());
        }
    }

    /**
     * Gets attributes, constructs proper RelativePath and returns FileVisitDetails for the given relativePath.
     *
     * @param path Path of the file
     * @param parentPath RelativePath of the parent
     * @param stopFlag transient flag to stop visiting
     * @param fileSystem for Chmod and Stat
     * @param linksStrategy links strategy
     * @return FileVisitDetails
     */
    public static FileVisitDetails getFileVisitDetails(
        Path path,
        RelativePath parentPath,
        AtomicBoolean stopFlag,
        FileSystem fileSystem,
        LinksStrategy linksStrategy
    ) {
        BasicFileAttributes attrs = getAttributes(path);
        return getFileVisitDetails(path, parentPath, attrs, stopFlag, fileSystem, linksStrategy);
    }

    private static FileVisitDetails createFileVisitDetails(
        File file,
        RelativePath relativePath,
        BasicFileAttributes attrs,
        AtomicBoolean stopFlag,
        FileSystem fileSystem,
        boolean preserveLink
    ) {
        if (attrs.isDirectory() && OperatingSystem.current() == OperatingSystem.WINDOWS) {
            // Workaround for https://github.com/gradle/gradle/issues/11577
            return new DefaultFileVisitDetails(file, relativePath, stopFlag, fileSystem, fileSystem, preserveLink);
        } else {
            return new AttributeBasedFileVisitDetails(file, relativePath, stopFlag, fileSystem, fileSystem, attrs, preserveLink);
        }
    }

    private static @Nullable BasicFileAttributes getAttributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            try {
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException next) {
                return null;
            }
        }
    }
}
