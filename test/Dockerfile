FROM node:14-alpine3.15

# make the 'app' folder the current working directory
WORKDIR /app

# Copy package[-lock].json
COPY package*.json ./

# Install node modules
RUN npm ci

# Copy project files
COPY . .

# Run the tests (after sleeping for a few seconds to let the server start up)
CMD [ "/bin/sh", "./perform-test-run.sh"]
