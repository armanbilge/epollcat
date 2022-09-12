#ifdef __linux__
#define _GNU_SOURCE
#include <errno.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/socket.h>

typedef unsigned short scalanative_sa_family_t;
struct scalanative_sockaddr {
    scalanative_sa_family_t sa_family;
    char sa_data[14];
};

int scalanative_convert_sockaddr(struct scalanative_sockaddr *raw_in,
                                 struct sockaddr **out, socklen_t *size);

int scalanative_convert_scalanative_sockaddr(struct sockaddr *raw_in,
                                             struct scalanative_sockaddr *out,
                                             socklen_t *size);

int epollcat_accept4(int socket, struct scalanative_sockaddr *address,
                       socklen_t *address_len, int flags) {
    struct sockaddr *converted_address;
    int convert_result = address != NULL ? // addr and addr_len can be NULL
                             scalanative_convert_sockaddr(
                                 address, &converted_address, address_len)
                                         : 0;

    int result;

    if (convert_result == 0) {
        result = accept4(socket, converted_address, address_len, flags);
        convert_result = address != NULL
                             ? scalanative_convert_scalanative_sockaddr(
                                   converted_address, address, address_len)
                             : 0;

        if (convert_result != 0) {
            errno = convert_result;
            result = -1;
        }
    } else {
        errno = convert_result;
        result = -1;
    }

    if (address != NULL)
        free(converted_address);
    return result;
}

#endif // linux