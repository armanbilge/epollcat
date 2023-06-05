#include <sys/epoll.h>
#include <stddef.h>

int scalanative_epoll_data_offset() {
  return offsetof(struct epoll_event, data);
}
