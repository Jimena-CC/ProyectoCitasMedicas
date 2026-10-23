import { redirect, type RouteObject } from 'react-router';

import { FRONT_DESK_ROLES } from '@/features/auth/roles';
import { RequireRole } from '@/features/auth/require-role';

import { AppointmentsPage } from './appointments-page';
import { BookAppointmentPage } from './book-appointment-page';
import { PatientCreatePage } from './patient-create-page';
import { PatientDetailPage } from './patient-detail-page';
import { PatientSearchPage } from './patient-search-page';

/**
 * Rutas del módulo de Ventanilla (admisión), roles FRONT_DESK y ADMIN.
 *
 *   /ventanilla                      → redirige a /ventanilla/pacientes
 *   /ventanilla/pacientes            buscar paciente por documento (CU-016); conserva ?documentType&documentNumber
 *   /ventanilla/pacientes/nuevo      registrar paciente (CU-016); acepta ?documentType&documentNumber precargados
 *   /ventanilla/pacientes/:patientId ficha del paciente: datos, seguro (CU-017) y sus citas; acepta ?tab=seguro|citas
 *   /ventanilla/agendar              agendar cita en ventanilla (CU-018), acepta ?paciente=<id>
 *   /ventanilla/citas                citas: reprogramar (CU-019) y anular (CU-020)
 */
export const admissionRoutes: RouteObject[] = [
  {
    path: 'ventanilla',
    element: <RequireRole roles={FRONT_DESK_ROLES} />,
    children: [
      // La redirección va en el loader: así el router la resuelve antes de dibujar, también durante una transición de vista.
      { index: true, loader: () => redirect('/ventanilla/pacientes') },
      { path: 'pacientes', element: <PatientSearchPage /> },
      { path: 'pacientes/nuevo', element: <PatientCreatePage /> },
      { path: 'pacientes/:patientId', element: <PatientDetailPage /> },
      { path: 'agendar', element: <BookAppointmentPage /> },
      { path: 'citas', element: <AppointmentsPage /> },
    ],
  },
];
