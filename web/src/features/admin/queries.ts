import { useQuery, type UseQueryOptions } from '@tanstack/react-query';

import { adminApi } from '@/lib/api/admin';
import type { DoctorDetail, LocationDetail, SpecialtyDetail, UserSummary } from '@/lib/api/types';

/** Claves de caché del módulo de administración (compartidas por las vistas y sus mutaciones). */
export const adminKeys = {
  users: ['admin', 'users'] as const,
  doctors: ['admin', 'doctors'] as const,
  specialties: ['admin', 'specialties'] as const,
  locations: ['admin', 'locations'] as const,
  slotsAll: ['admin', 'slots'] as const,
  slots: (doctorId: number, from: string, to: string) => ['admin', 'slots', doctorId, from, to] as const,
};

type Extra<T> = Omit<UseQueryOptions<T, Error, T, readonly string[]>, 'queryKey' | 'queryFn'>;

export function useUsersQuery(options?: Extra<UserSummary[]>) {
  return useQuery({
    queryKey: adminKeys.users,
    queryFn: adminApi.listUsers,
    ...options,
  });
}

export function useDoctorsQuery(options?: Extra<DoctorDetail[]>) {
  return useQuery({
    queryKey: adminKeys.doctors,
    queryFn: adminApi.listDoctors,
    ...options,
  });
}

export function useSpecialtiesQuery(options?: Extra<SpecialtyDetail[]>) {
  return useQuery({
    queryKey: adminKeys.specialties,
    queryFn: adminApi.listSpecialties,
    ...options,
  });
}

export function useLocationsQuery(options?: Extra<LocationDetail[]>) {
  return useQuery({
    queryKey: adminKeys.locations,
    queryFn: adminApi.listLocations,
    ...options,
  });
}
