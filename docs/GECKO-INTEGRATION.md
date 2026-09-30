# Gecko Integration Plan

## Purpose

Meridian is intended to become a Windows browser based on Mozilla Firefox/Gecko. The existing Meridian repository contains a small interface prototype; it is not a browser engine and should not be treated as a drop-in replacement for Firefox's source tree.

## Repository layout

Keep the repositories/workspaces separate:

- **Meridian interface repository**: branding, prototype UI, design references, project documentation.
- **Firefox source checkout or maintained fork**: the actual browser application and Gecko engine, including upstream build files and third-party notices.
- **Build output**: generated files kept outside version control.

Do not copy the entire Firefox source tree into the existing interface repository. Keep Mozilla's source history, build system, licensing notices, and upstream update path intact.

## Staged implementation

### Stage 1: Prepare the Windows build environment

1. Back up important work and ensure there is ample free disk space.
2. Install MozillaBuild using Mozilla's official Windows build instructions: <https://firefox-source-docs.mozilla.org/setup/windows_build.html>.
3. Open the MozillaBuild shell, which supplies the expected build environment and tools.

### Stage 2: Obtain Mozilla's source

Use Mozilla's documented source checkout workflow. From the MozillaBuild shell, clone the mozilla-central source repository into a separate working directory:

~~~sh
hg clone https://hg.mozilla.org/mozilla-central
cd mozilla-central
~~~

Mozilla's source repository uses Mercurial in its documented workflow. Do not assume that a normal GitHub clone is interchangeable with this checkout.

### Stage 3: Configure and build an unmodified baseline

From the source directory, run:

~~~sh
./mach bootstrap
./mach build
./mach run
~~~

Follow any prompts from bootstrap and resolve prerequisite errors before changing browser code. The first build can take a long time, especially on a low-power dual-core laptop with 8 GB RAM. Close other applications, keep Windows' page file enabled, connect the laptop to power, and avoid starting with multiple build directories.

If the build fails, save the full terminal output and diagnose the first meaningful error rather than repeatedly rerunning the entire build.

### Stage 4: Record the baseline

Before modifying source, record:

- The exact source revision.
- Whether bootstrap completed.
- Whether the build completed.
- Whether the browser launches.
- Any warnings or known failures.

This provides a known-good point to compare against later Meridian changes.

### Stage 5: Integrate Meridian incrementally

Only after the baseline works:

1. Identify the relevant Firefox browser UI files and build configuration for the chosen source revision.
2. Apply Meridian's monochrome visual design in small, reviewable changes.
3. Preserve upstream files and licenses; avoid deleting components just because they are not immediately visible in the UI.
4. Build and launch after each logical change.
5. Test navigation, tabs, downloads, settings, accessibility, and updates before considering the prototype integrated.

### Stage 6: Review privacy and optional components

Do not remove engine or browser components blindly. First document what each component does, its dependencies, security implications, and whether it is controlled by a build option or preference. Keep security updates and essential web-platform functionality intact.

Privacy claims should only be made after the relevant behavior has been implemented and tested.

## Hardware notes for the NY-14

The laptop has an Intel Pentium Gold 4425Y, 8 GB RAM, and approximately 142 GB free storage. Storage is workable for an initial source checkout and one build, but the CPU and RAM are significant constraints. Expect the initial build and later full rebuilds to be slow. Keep free space available for Windows and temporary build files.

## Important licensing note

Firefox and Gecko are open-source projects, but that does not mean every component has identical terms. Preserve upstream license files and notices, review licenses for bundled dependencies, and include required notices in any redistributed build. This document is a technical plan, not a claim that Meridian has already incorporated or built Firefox.
