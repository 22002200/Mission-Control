# Product Specification

## 1. Product Overview

Mission Control is a multi-tenant B2B platform for space organisations to plan missions and intelligently
assign crew based on skills, availability and assignment history.

## 2. Users

Each organisation will have users with the following roles and abilities.
Each user can only have one role.

### Director
- Manage organisation settings.
- Has full visibility of all missions within organisation.
- Can approve or reject missions.

### Mission Lead
- Create and plan missions within organisation.
- Define crew requirements for missions.
- Submit missions for approval by director.
- Run crew matching engine and assign crew members to mission once approved.
- Cannot approve missions.
- Has visibility of missions they own.
- Can own any number of missions simultaneously.

### Crew Member
- Manage own profile settings.
- Accept or decline assignments.
- Can only have one assignment at a time.
- Available unless assigned to a mission.
- Have a skill profile.
- Have an assignment history.
- Cannot create or approve missions.

## 3. Core Capabilities

### Authentication

- Users can login via email and password.
- Successful authentication produces a JWT, identifying user, organisation, and role.
- Users must only see data belonging to their organisation.
- Users can logout.

### Mission Management

- Mission Leads can create new Missions and fill in number of crew members and required skill profiles
for each, as well as Mission timelines.
- Mission Leads can then submit the Mission plan for approval from Directors.
- If rejected, Mission Leads can either abort the Mission or edit and resubmit for approval.
- If approved, Mission Leads will run the Crew Matching engine and select desired crew members.
- Selected crew members can see the Mission on their dashboard and accept or decline.
If they accept, their availability and assignment history will be updated.
- Mission Leads and Directors can abort a Mission at any time.
- Mission Leads can edit Mission details at any time, and resubmit for approval from Directors.

### Crew Matching

Crew Matching will suggest Crew Members for a Mission based on the following criteria:

1. Crew Member must be available during Mission timeline.
2. Crew Member's skill profile will be used to match Mission requirements.
3. Crew Member's assignment history will be factored in for ranking.

### Dashboard

Upon login users will see a dashboard. The dashboard will only display data from the logged-in user's
organisation.
- Crew Members will see a list of active, pending, and completed Missions they've been assigned to.
- Mission Leads will see a list of Missions they own, and can create new Missions.
- Directors will see all Missions, as well as org-level metrics.

## 4. Mission Lifecycle

A mission can have the following lifecycles:

- PLAN → PENDING_APPROVAL → APPROVED → ACTIVE → CLOSED
- PLAN → PENDING_APPROVAL → REJECTED → CLOSED
- PLAN → PENDING_APPROVAL → REJECTED → PLAN

Rejected missions can either be closed or returned to PLAN phase.
Missions can be closed from any phase.

## 5. Product Constraints

- Multiple organisations must be supported.
- Organisation data must never leak between tenants.
- Application must run locally using Docker Compose.

## 7. Out of Scope

- External identity providers
- Email notifications
- Mobile applications
- Real-time collaboration
- Production deployment
