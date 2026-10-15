# SafeSphere — The Safety App

A women's safety and emergency assistance project designed to help users respond quickly to potentially unsafe situations.

## Project Overview

SafeSphere is organized into two main components: an Android safety application and a web-based admin dashboard. The project focuses on emergency detection, SOS alerts, and incident management.

## Project Structure

### 1. `/android` — Android Safety Application

The Android application provides safety features designed to help users trigger emergency alerts.

**Features**
- **Emergency Detection:** Detects configured emergency triggers.
- **Shake Trigger:** Initiates an SOS workflow when the device is shaken.
- **Keyword Detection:** Detects configured voice keywords for emergency activation.
- **Emergency Alerts:** Supports emergency response workflows.
- **Fake Call:** Provides a fake-call feature as an additional personal safety option.

**How to Run**

1. Clone or download this repository.
2. Open the `/android` folder in Android Studio.
3. Allow Gradle to sync and install the required dependencies.
4. Connect an Android device or start an emulator.
5. Build and run the application.

### 2. `/admin` — Admin Dashboard

The admin dashboard is a web application built with Next.js and Supabase. It is intended to support incident management and analytics.

**Technologies Used**
- Next.js
- Supabase
- JavaScript / TypeScript
- Web development tools

**Intended Features**
- Admin dashboard interface
- Incident management
- Analytics and reporting

**How to Run Locally**

Make sure Node.js and npm are installed on your system.

```bash
cd admin
npm install
npm run dev
```

Open the following URL in your browser:

`http://localhost:3000/admin/login`

**Development Status:** The admin dashboard is under development. Login and API integration require further debugging, and all functionality has not yet been verified.

## Technologies Used

| Component | Technologies |
|---|---|
| Android Application | Java, Android Studio, Android SDK |
| Speech Recognition | Vosk API |
| Admin Dashboard | Next.js |
| Backend / Data Services | Supabase |

## Repository Structure

```text
SafeSphere/
├── android/    # Android safety application
├── admin/      # Admin dashboard
└── README.md   # Project documentation
```

## Project Goals

- Provide accessible emergency-trigger mechanisms.
- Support quick SOS workflows for personal safety.
- Offer an admin interface for incident management and analytics.
- Explore the integration of mobile safety features with a web-based dashboard.

## Current Status

- **Android Application:** Contains emergency detection, shake-trigger, keyword detection, and fake-call features.
- **Admin Dashboard:** Under development; login/API integration needs further debugging.

## Disclaimer

SafeSphere is a safety-support project and should not be considered a replacement for official emergency services. Users should not rely on the application as their only means of obtaining help.

## License

Refer to the repository's `LICENSE` file for licensing terms.
