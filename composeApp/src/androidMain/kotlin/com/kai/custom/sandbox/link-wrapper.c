#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/syscall.h>

static int (*real_link)(const char *, const char *) = NULL;
static int (*real_linkat)(int, const char *, int, const char *, int) = NULL;
static int trace_enabled = -1; /* -1 = uninitialized, 0 = off, 1 = on */

static void init(void) {
    char *env = getenv("LINK_WRAPPER_TRACE");
    trace_enabled = (env && env[0] == '1') ? 1 : 0;
    real_link = (int (*)(const char *, const char *))dlsym(RTLD_NEXT, "link");
    real_linkat = (int (*)(int, const char *, int, const char *, int))dlsym(RTLD_NEXT, "linkat");
}

static int copy_file(const char *src, const char *dst) {
    int in_fd, out_fd;
    struct stat st;
    char buf[65536];
    ssize_t nread, nwritten;
    int ret = -1;

    in_fd = open(src, O_RDONLY);
    if (in_fd < 0) {
        if (trace_enabled) fprintf(stderr, "[link-wrapper] open src=%s: %s\n", src, strerror(errno));
        return -1;
    }

    if (fstat(in_fd, &st) < 0) {
        if (trace_enabled) fprintf(stderr, "[link-wrapper] fstat %s: %s\n", src, strerror(errno));
        close(in_fd);
        return -1;
    }

    out_fd = open(dst, O_WRONLY | O_CREAT | O_TRUNC, st.st_mode & 07777);
    if (out_fd < 0) {
        if (trace_enabled) fprintf(stderr, "[link-wrapper] open dst=%s: %s\n", dst, strerror(errno));
        close(in_fd);
        return -1;
    }

    while ((nread = read(in_fd, buf, sizeof(buf))) > 0) {
        char *p = buf;
        while (nread > 0) {
            nwritten = write(out_fd, p, nread);
            if (nwritten < 0) {
                if (trace_enabled) fprintf(stderr, "[link-wrapper] write: %s\n", strerror(errno));
                goto done;
            }
            nread -= nwritten;
            p += nwritten;
        }
    }

    if (nread < 0) {
        if (trace_enabled) fprintf(stderr, "[link-wrapper] read: %s\n", strerror(errno));
        goto done;
    }

    /* Preserve ownership and timestamps best-effort */
    fchown(out_fd, st.st_uid, st.st_gid);
    ret = 0;

done:
    close(in_fd);
    close(out_fd);
    return ret;
}

int link(const char *oldpath, const char *newpath) {
    if (!real_link) init();

    if (trace_enabled)
        fprintf(stderr, "[link-wrapper] link(\"%s\", \"%s\")\n", oldpath, newpath);

    int result = real_link(oldpath, newpath);
    if (result == 0) return 0;

    /* EPERM/EACCES typically means protected_hardlinks or fs restrictions */
    if (errno == EPERM || errno == EACCES || errno == ENOSYS || errno == ENOTSUP) {
        if (trace_enabled)
            fprintf(stderr, "[link-wrapper] link() failed with %s, falling back to copy\n", strerror(errno));

        result = copy_file(oldpath, newpath);
        if (result == 0) return 0;

        /* Copy also failed, restore original errno */
        errno = EPERM;
    }

    return -1;
}

int linkat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, int flags) {
    if (!real_linkat) init();

    if (trace_enabled)
        fprintf(stderr, "[link-wrapper] linkat(%d, \"%s\", %d, \"%s\", %d)\n",
                olddirfd, oldpath, newdirfd, newpath, flags);

    int result = real_linkat(olddirfd, oldpath, newdirfd, newpath, flags);
    if (result == 0) return 0;

    if (errno == EPERM || errno == EACCES || errno == ENOSYS || errno == ENOTSUP) {
        if (trace_enabled)
            fprintf(stderr, "[link-wrapper] linkat() failed with %s, falling back to copy\n", strerror(errno));

        /* Resolve dirfd to path (AT_FDCWD = current dir) */
        const char *resolved_old = oldpath;
        const char *resolved_new = newpath;
        char old_buf[1024], new_buf[1024];

        if (olddirfd != AT_FDCWD && oldpath[0] != '/') {
            /* Absolute paths don't need dirfd */
            /* We can't easily resolve dirfd; fallback to /proc/self/fd/N */
            snprintf(old_buf, sizeof(old_buf), "/proc/self/fd/%d/%s", olddirfd, oldpath);
            resolved_old = old_buf;
        }
        if (newdirfd != AT_FDCWD && newpath[0] != '/') {
            snprintf(new_buf, sizeof(new_buf), "/proc/self/fd/%d/%s", newdirfd, newpath);
            resolved_new = new_buf;
        }

        result = copy_file(resolved_old, resolved_new);
        if (result == 0) return 0;

        errno = EPERM;
    }

    return -1;
}
