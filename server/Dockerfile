FROM node:20-alpine
WORKDIR /app
COPY server.js ./
COPY public ./public
ENV PORT=8080
ENV DATA_DIR=/data
EXPOSE 8080
CMD ["node", "server.js"]
