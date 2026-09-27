package com.leaguemate.api.service;

import com.leaguemate.api.dto.TeamMemberResponse;
import com.leaguemate.api.entity.*;
import com.leaguemate.api.exception.ResourceConflictException;
import com.leaguemate.api.exception.ResourceNotFoundException;
import com.leaguemate.api.repository.TeamMemberRepository;
import com.leaguemate.api.repository.TeamRepository;
import com.leaguemate.api.repository.UserRepository;
import com.leaguemate.api.service.impl.TeamMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceImplTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamMemberServiceImpl teamMemberService;

    private Team team;
    private User user;
    private TeamMember member;

    @BeforeEach
    void setUp() {
        team = new Team();
        team.setId(1L);
        team.setName("Straw Hat FC");

        user = new User();
        user.setId(1L);
        user.setUsername("manuel22");
        user.setFirstName("Manuel");
        user.setLastName("Barbagallo");
        user.setRole(Role.USER);

        member = new TeamMember();
        member.setId(1L);
        member.setTeam(team);
        member.setUser(user);
        member.setTeamRole(TeamRole.CAPTAIN);
        member.setJoinedAt(LocalDateTime.now());
    }

    @Test
    void addMemberToTeam_Success() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenReturn(member);

        TeamMemberResponse response = teamMemberService.addMemberToTeam(1L, 1L, TeamRole.CAPTAIN);

        assertNotNull(response);
        assertEquals("manuel22", response.username());
        assertEquals("CAPTAIN", response.teamRole());
        verify(teamMemberRepository, times(1)).save(any(TeamMember.class));
    }

    @Test
    void addMemberToTeam_ThrowsNotFound_WhenTeamDoesNotExist() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.addMemberToTeam(99L, 1L, TeamRole.PLAYER));
    }

    @Test
    void addMemberToTeam_ThrowsNotFound_WhenUserDoesNotExist() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.addMemberToTeam(1L, 99L, TeamRole.PLAYER));
    }

    @Test
    void addMemberToTeam_ThrowsConflict_WhenUserAlreadyMember() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).thenReturn(true);

        assertThrows(ResourceConflictException.class,
                () -> teamMemberService.addMemberToTeam(1L, 1L, TeamRole.PLAYER));
        verify(teamMemberRepository, never()).save(any(TeamMember.class));
    }

    @Test
    void addMemberToTeam_ChecksDuplicateWithoutLoadingWholeTable() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(teamMemberRepository.existsByTeamIdAndUserId(1L, 1L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenReturn(member);

        teamMemberService.addMemberToTeam(1L, 1L, TeamRole.PLAYER);

        verify(teamMemberRepository, never()).findAll();
    }

    @Test
    void getMembersByTeam_ReturnsList() {
        when(teamRepository.existsById(1L)).thenReturn(true);
        when(teamMemberRepository.findByTeamIdWithUserAndTeam(1L)).thenReturn(List.of(member));

        List<TeamMemberResponse> members = teamMemberService.getMembersByTeam(1L);

        assertEquals(1, members.size());
        assertEquals("manuel22", members.get(0).username());
        assertEquals("Straw Hat FC", members.get(0).teamName());
    }

    @Test
    void getMembersByTeam_ThrowsNotFound_WhenTeamDoesNotExist() {
        when(teamRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.getMembersByTeam(99L));
        verify(teamMemberRepository, never()).findByTeamIdWithUserAndTeam(anyLong());
    }

    @Test
    void removeMemberFromTeam_Success() {
        when(teamMemberRepository.findByIdAndTeamId(1L, 1L)).thenReturn(Optional.of(member));

        teamMemberService.removeMemberFromTeam(1L, 1L);

        verify(teamMemberRepository, times(1)).delete(member);
    }

    @Test
    void removeMemberFromTeam_ThrowsNotFound_WhenMemberDoesNotExist() {
        when(teamMemberRepository.findByIdAndTeamId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.removeMemberFromTeam(1L, 99L));
    }

    @Test
    void removeMemberFromTeam_ThrowsNotFound_WhenMemberBelongsToAnotherTeam() {
        when(teamMemberRepository.findByIdAndTeamId(1L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> teamMemberService.removeMemberFromTeam(2L, 1L));
        verify(teamMemberRepository, never()).delete(any(TeamMember.class));
    }
}