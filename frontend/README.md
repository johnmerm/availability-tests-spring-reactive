# Booking System - Vue Frontend

Simple single-page application (SPA) built with Vue 3 (CDN) for testing the Spring Boot Reactive booking API.

## Features

- **Events Tab**: Browse all available events with pagination
- **Event Details**: View dates and real-time availability for each event
- **Book Tickets**: Create reservations with multiple ticket types
- **Confirm Payment**: Finalize reservations with payment references

## Usage

### Quick Start

1. **Start the backend:**
   ```bash
   cd ..
   docker-compose up -d
   ```

2. **Open the frontend:**
   ```bash
   # Option 1: Simple HTTP server with Python
   python3 -m http.server 8081

   # Option 2: Node.js http-server (if installed)
   npx http-server -p 8081

   # Option 3: PHP built-in server
   php -S localhost:8081
   ```

3. **Open in browser:**
   ```
   http://localhost:8081
   ```

4. **Configure API URL:**
   - The default is `http://localhost:8080`
   - Change it in the input field if your backend runs elsewhere

## No Build Required

This is a standalone HTML file that loads:
- Vue 3 from CDN (unpkg.com)
- Axios from CDN for HTTP requests
- All styling inline

No npm, webpack, or build tools needed!

## API Endpoints Used

- `GET /events?page={page}&size={size}` - List events
- `GET /events/{id}?startDate={date}&endDate={date}` - Event details with availability
- `POST /events/{id}/{date}/{time}` - Create reservation
- `POST /reservation/{id}` - Confirm payment

## Testing Workflow

1. **View Events** - Browse available events
2. **Check Availability** - Click an event to see dates and available tickets
3. **Create Reservation** - Go to "Book Tickets" tab and fill the form
4. **Confirm Payment** - Use the reservation ID to confirm within 60 seconds

## CORS Note

If you get CORS errors, make sure your backend has CORS enabled. The backend already includes CORS configuration in `WebConfig.java` that allows all origins.

## Screenshot

The app includes:
- Purple gradient background
- Card-based UI
- Real-time availability indicators (green/orange/red)
- Form validation
- Loading states
- Error handling
