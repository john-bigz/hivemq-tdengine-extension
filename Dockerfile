ARG TAG=latest

# (1)
FROM hivemq/hivemq-ce:${TAG}

COPY my-entrypoint.sh /opt/my-entrypoint.sh
RUN mkdir -p /opt/hivemq/extensions/tdengine-extension
COPY target/tdengine-extension /opt/hivemq-ce-2020.4/extensions/tdengine-extension

WORKDIR /root
COPY TDengine-server-2.x-latest-Linux-x64.tar.gz /root/
RUN tar -zxf TDengine-server-2.x-latest-Linux-x64.tar.gz

WORKDIR /root/TDengine-server/
RUN /root/TDengine-server/install.sh -e no

ENV LD_LIBRARY_PATH="$LD_LIBRARY_PATH:/usr/lib"
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8
EXPOSE 6030 6031 6032 6033 6034 6035 6036 6037 6038 6039 6040 6041 6042
#CMD ["taosd"]
VOLUME [ "/var/lib/taos", "/var/log/taos","/etc/taos/" ]

# (5)
RUN chmod +x /opt/my-entrypoint.sh

# (6)
ENTRYPOINT ["/opt/my-entrypoint.sh"]
#CMD ["/opt/hivemq/bin/run.sh"]
